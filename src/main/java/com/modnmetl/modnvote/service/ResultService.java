package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AnonymousBallotPreferenceDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Read-only service for public poll result displays.
 *
 * Important design notes:
 * - results are derived from anonymous ballots only
 * - participation data is not used for vote-content reporting
 * - this service does not mutate poll state or publish finality markers
 *
 * For ranked single-winner polls, the displayed candidate table in this first
 * pass is a first-preference tally view, while the winner is determined by a
 * deterministic IRV recount over the stored anonymous ballots.
 */
public final class ResultService {

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;
    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotPreferenceDao anonymousBallotPreferenceDao;

    public ResultService(DatabaseManager databaseManager,
                         Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollDao = new PollDao(databaseManager);
        this.pollOptionDao = new PollOptionDao(databaseManager);
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotPreferenceDao = new AnonymousBallotPreferenceDao(databaseManager);
    }

    public PollResult getPollResult(long pollId) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }

            if (poll.status() != PollStatus.CLOSED) {
                throw new PollServiceException("Poll #" + pollId + " is still open. Please try again once it has closed.");
            }

            List<PollOption> options = pollOptionDao.findOptionsByPollId(pollId);
            if (options.isEmpty()) {
                throw new PollServiceException("Poll #" + pollId + " has no selectable options.");
            }

            List<StoredBallot> ballots = loadBallots(pollId);

            return switch (poll.pollType()) {
                case YES_NO -> buildYesNoResult(poll, options, ballots);
                case RANKED_SINGLE_WINNER -> buildRankedSingleWinnerResult(poll, options, ballots);
                default -> throw new PollServiceException(
                        "Poll type " + poll.pollType().name() + " does not yet have a public result display."
                );
            };
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to build result for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to build result for poll #" + pollId, e);
        }
    }

    private List<StoredBallot> loadBallots(long pollId) throws Exception {
        List<AnonymousBallotDao.StoredAnonymousBallot> storedBallots =
                anonymousBallotDao.findAnonymousBallotsByPollId(pollId);

        List<StoredBallot> out = new ArrayList<>(storedBallots.size());

        for (AnonymousBallotDao.StoredAnonymousBallot ballot : storedBallots) {
            List<AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference> preferences =
                    anonymousBallotPreferenceDao.findPreferencesByAnonymousBallotId(ballot.anonymousBallotId());

            List<Long> orderedOptionIds = preferences.stream()
                    .sorted(Comparator.comparingInt(
                            AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference::rankPosition
                    ))
                    .map(AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference::optionId)
                    .toList();

            out.add(new StoredBallot(ballot.anonymousBallotId(), orderedOptionIds));
        }

        return List.copyOf(out);
    }

    private PollResult buildYesNoResult(Poll poll,
                                        List<PollOption> options,
                                        List<StoredBallot> ballots) throws PollServiceException {
        if (options.size() != 2) {
            throw new PollServiceException("YES_NO poll #" + poll.pollId() + " must have exactly 2 options.");
        }

        boolean hasYesKey = false;
        boolean hasNoKey = false;

        for (PollOption option : options) {
            String key = option.key().trim().toLowerCase(java.util.Locale.ROOT);
            if ("yes".equals(key)) {
                hasYesKey = true;
            } else if ("no".equals(key)) {
                hasNoKey = true;
            } else {
                throw new PollServiceException(
                        "YES_NO poll #" + poll.pollId() + " contains non-canonical option key '" + option.key() + "'."
                );
            }
        }

        if (!hasYesKey || !hasNoKey) {
            throw new PollServiceException(
                    "YES_NO poll #" + poll.pollId() + " must contain canonical option keys 'yes' and 'no'."
            );
        }

        Map<Long, Integer> counts = initializeCounts(options);

        for (StoredBallot ballot : ballots) {
            if (ballot.orderedOptionIds().isEmpty()) {
                continue;
            }

            long selectedOptionId = ballot.orderedOptionIds().get(0);
            if (counts.containsKey(selectedOptionId)) {
                counts.put(selectedOptionId, counts.get(selectedOptionId) + 1);
            }
        }

        List<OptionTally> orderedTallies = options.stream()
                .map(option -> new OptionTally(
                        option.optionId(),
                        option.key(),
                        option.displayName(),
                        counts.getOrDefault(option.optionId(), 0)
                ))
                .sorted(Comparator
                        .comparingInt(OptionTally::votes).reversed()
                        .thenComparing(OptionTally::optionName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(OptionTally::optionId))
                .toList();

        OptionTally winner = orderedTallies.isEmpty() ? null : orderedTallies.get(0);

        return new PollResult(
                poll.pollId(),
                poll.title(),
                poll.pollType(),
                ballots.size(),
                winner != null ? winner.optionName() : null,
                orderedTallies
        );
    }

    private PollResult buildRankedSingleWinnerResult(Poll poll,
                                                     List<PollOption> options,
                                                     List<StoredBallot> ballots) throws PollServiceException {
        Map<Long, PollOption> optionsById = toOptionsById(options);

        Map<Long, Integer> firstPreferenceCounts = initializeCounts(options);
        for (StoredBallot ballot : ballots) {
            Long firstPreference = firstValidPreference(ballot.orderedOptionIds(), optionsById.keySet());
            if (firstPreference != null) {
                firstPreferenceCounts.put(firstPreference, firstPreferenceCounts.get(firstPreference) + 1);
            }
        }

        Long winnerOptionId = determineIrVWinner(options, ballots, optionsById.keySet());
        String winnerName = winnerOptionId != null && optionsById.containsKey(winnerOptionId)
                ? optionsById.get(winnerOptionId).displayName()
                : null;

        List<OptionTally> orderedTallies = options.stream()
                .map(option -> new OptionTally(
                        option.optionId(),
                        option.key(),
                        option.displayName(),
                        firstPreferenceCounts.getOrDefault(option.optionId(), 0)
                ))
                .sorted(Comparator
                        .comparingInt(OptionTally::votes).reversed()
                        .thenComparing(OptionTally::optionName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(OptionTally::optionId))
                .toList();

        return new PollResult(
                poll.pollId(),
                poll.title(),
                poll.pollType(),
                ballots.size(),
                winnerName,
                orderedTallies
        );
    }

    private Long determineIrVWinner(List<PollOption> options,
                                    List<StoredBallot> ballots,
                                    Set<Long> validOptionIds) {
        LinkedHashSet<Long> active = options.stream()
                .sorted(Comparator.comparingInt(PollOption::displayOrder).thenComparingLong(PollOption::optionId))
                .map(PollOption::optionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        while (!active.isEmpty()) {
            Map<Long, Integer> roundCounts = new LinkedHashMap<>();
            for (Long optionId : active) {
                roundCounts.put(optionId, 0);
            }

            int countedBallots = 0;

            for (StoredBallot ballot : ballots) {
                Long vote = firstValidPreference(ballot.orderedOptionIds(), active);
                if (vote != null && validOptionIds.contains(vote)) {
                    roundCounts.put(vote, roundCounts.get(vote) + 1);
                    countedBallots++;
                }
            }

            if (active.size() == 1) {
                return active.iterator().next();
            }

            for (Map.Entry<Long, Integer> entry : roundCounts.entrySet()) {
                if (entry.getValue() * 2 > countedBallots) {
                    return entry.getKey();
                }
            }

            Long optionToEliminate = active.stream()
                    .min(Comparator
                            .comparingInt((Long optionId) -> roundCounts.getOrDefault(optionId, 0))
                            .thenComparingLong(optionId -> optionId))
                    .orElse(null);

            if (optionToEliminate == null) {
                return null;
            }

            active.remove(optionToEliminate);
        }

        return null;
    }

    private Long firstValidPreference(List<Long> orderedOptionIds, Set<Long> allowedOptionIds) {
        for (Long optionId : orderedOptionIds) {
            if (allowedOptionIds.contains(optionId)) {
                return optionId;
            }
        }
        return null;
    }

    private Map<Long, PollOption> toOptionsById(List<PollOption> options) {
        Map<Long, PollOption> out = new LinkedHashMap<>();
        for (PollOption option : options) {
            out.put(option.optionId(), option);
        }
        return Map.copyOf(out);
    }

    private Map<Long, Integer> initializeCounts(List<PollOption> options) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (PollOption option : options) {
            counts.put(option.optionId(), 0);
        }
        return counts;
    }

    private record StoredBallot(
            long anonymousBallotId,
            List<Long> orderedOptionIds
    ) {
        private StoredBallot {
            Objects.requireNonNull(orderedOptionIds, "orderedOptionIds");
            orderedOptionIds = List.copyOf(orderedOptionIds);
        }
    }

    public record PollResult(
            long pollId,
            String pollTitle,
            PollType pollType,
            int totalVotes,
            String winnerName,
            List<OptionTally> tallies
    ) {
        public PollResult {
            Objects.requireNonNull(pollTitle, "pollTitle");
            Objects.requireNonNull(pollType, "pollType");
            Objects.requireNonNull(tallies, "tallies");
            tallies = List.copyOf(tallies);
        }
    }

    public record OptionTally(
            long optionId,
            String optionKey,
            String optionName,
            int votes
    ) {
        public OptionTally {
            Objects.requireNonNull(optionKey, "optionKey");
            Objects.requireNonNull(optionName, "optionName");
        }
    }
}