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
 * For ranked single-winner polls, the public result now includes deterministic
 * IRV round snapshots so presentation layers can distinguish first-preference
 * support from the final transferred winner.
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
                case LINKED_OFFICES -> throw new PollServiceException(
                        "Linked Offices result calculation is not implemented yet."
                );
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

        List<OptionTally> orderedTallies = sortTallies(options, counts);
        OptionTally winner = orderedTallies.isEmpty() ? null : orderedTallies.get(0);

        return new PollResult(
                poll.pollId(),
                poll.title(),
                poll.pollType(),
                ballots.size(),
                winner != null ? winner.optionName() : null,
                orderedTallies,
                List.of(),
                winner,
                0
        );
    }

    private PollResult buildRankedSingleWinnerResult(Poll poll,
                                                     List<PollOption> options,
                                                     List<StoredBallot> ballots) {
        Map<Long, PollOption> optionsById = toOptionsById(options);
        RankedChoiceReport report = determineRankedChoiceReport(options, ballots, optionsById.keySet());

        String winnerName = report.winnerOptionId() != null && optionsById.containsKey(report.winnerOptionId())
                ? optionsById.get(report.winnerOptionId()).displayName()
                : null;

        List<OptionTally> firstPreferenceTallies = report.rounds().isEmpty()
                ? sortTallies(options, initializeCounts(options))
                : report.rounds().get(0).tallies();

        return new PollResult(
                poll.pollId(),
                poll.title(),
                poll.pollType(),
                ballots.size(),
                winnerName,
                firstPreferenceTallies,
                report.rounds(),
                report.winnerTally(),
                report.exhaustedBallots()
        );
    }

    private RankedChoiceReport determineRankedChoiceReport(List<PollOption> options,
                                                           List<StoredBallot> ballots,
                                                           Set<Long> validOptionIds) {
        LinkedHashSet<Long> active = options.stream()
                .sorted(Comparator.comparingInt(PollOption::displayOrder).thenComparingLong(PollOption::optionId))
                .map(PollOption::optionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<Long, PollOption> optionsById = toOptionsById(options);
        List<RankedChoiceRound> rounds = new ArrayList<>();
        Long winnerOptionId = null;
        OptionTally winnerTally = null;
        int finalExhaustedBallots = 0;
        if (ballots.isEmpty()) {
            return new RankedChoiceReport(null, null, 0, List.of());
        }
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

            int exhaustedBallots = ballots.size() - countedBallots;
            Long majorityWinner = findMajorityWinner(roundCounts, countedBallots);
            boolean lastCandidateStanding = active.size() == 1;
            boolean finalRound = majorityWinner != null || lastCandidateStanding;
            Long roundWinnerOptionId = majorityWinner;
            if (roundWinnerOptionId == null && lastCandidateStanding) {
                roundWinnerOptionId = active.iterator().next();
            }

            Long optionToEliminate = null;
            if (!finalRound) {
                optionToEliminate = active.stream()
                        .min(Comparator
                                .comparingInt((Long optionId) -> roundCounts.getOrDefault(optionId, 0))
                                .thenComparingLong(optionId -> optionId))
                        .orElse(null);
            }

            List<OptionTally> tallies = sortActiveTallies(active, optionsById, roundCounts);
            OptionTally eliminatedTally = optionToEliminate == null
                    ? null
                    : toTally(optionsById.get(optionToEliminate), roundCounts.getOrDefault(optionToEliminate, 0));
            OptionTally roundWinnerTally = roundWinnerOptionId == null
                    ? null
                    : toTally(optionsById.get(roundWinnerOptionId), roundCounts.getOrDefault(roundWinnerOptionId, 0));

            rounds.add(new RankedChoiceRound(
                    rounds.size() + 1,
                    tallies,
                    countedBallots,
                    exhaustedBallots,
                    eliminatedTally,
                    roundWinnerTally,
                    finalRound
            ));

            if (finalRound) {
                winnerOptionId = roundWinnerOptionId;
                winnerTally = roundWinnerTally;
                finalExhaustedBallots = exhaustedBallots;
                break;
            }

            if (optionToEliminate == null) {
                break;
            }

            active.remove(optionToEliminate);
        }

        return new RankedChoiceReport(winnerOptionId, winnerTally, finalExhaustedBallots, rounds);
    }

    private Long findMajorityWinner(Map<Long, Integer> roundCounts, int countedBallots) {
        for (Map.Entry<Long, Integer> entry : roundCounts.entrySet()) {
            if (entry.getValue() * 2 > countedBallots) {
                return entry.getKey();
            }
        }
        return null;
    }

    private List<OptionTally> sortTallies(List<PollOption> options, Map<Long, Integer> counts) {
        return options.stream()
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
    }

    private List<OptionTally> sortActiveTallies(Set<Long> active,
                                                Map<Long, PollOption> optionsById,
                                                Map<Long, Integer> counts) {
        return active.stream()
                .map(optionId -> toTally(optionsById.get(optionId), counts.getOrDefault(optionId, 0)))
                .sorted(Comparator
                        .comparingInt(OptionTally::votes).reversed()
                        .thenComparing(OptionTally::optionName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(OptionTally::optionId))
                .toList();
    }

    private OptionTally toTally(PollOption option, int votes) {
        return new OptionTally(
                option.optionId(),
                option.key(),
                option.displayName(),
                votes
        );
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

    private record RankedChoiceReport(
            Long winnerOptionId,
            OptionTally winnerTally,
            int exhaustedBallots,
            List<RankedChoiceRound> rounds
    ) {
        private RankedChoiceReport {
            Objects.requireNonNull(rounds, "rounds");
            rounds = List.copyOf(rounds);
        }
    }

    public record PollResult(
            long pollId,
            String pollTitle,
            PollType pollType,
            int totalVotes,
            String winnerName,
            List<OptionTally> tallies,
            List<RankedChoiceRound> rankedChoiceRounds,
            OptionTally finalWinnerTally,
            int exhaustedBallots
    ) {
        public PollResult {
            Objects.requireNonNull(pollTitle, "pollTitle");
            Objects.requireNonNull(pollType, "pollType");
            Objects.requireNonNull(tallies, "tallies");
            Objects.requireNonNull(rankedChoiceRounds, "rankedChoiceRounds");
            tallies = List.copyOf(tallies);
            rankedChoiceRounds = List.copyOf(rankedChoiceRounds);
        }
    }

    public record RankedChoiceRound(
            int roundNumber,
            List<OptionTally> tallies,
            int activeBallots,
            int exhaustedBallots,
            OptionTally eliminatedTally,
            OptionTally winnerTally,
            boolean finalRound
    ) {
        public RankedChoiceRound {
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
