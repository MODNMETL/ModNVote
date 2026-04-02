package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.BallotPreference;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.BallotDao;
import com.modnmetl.modnvote.storage.BallotPreferenceDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service layer for ballot submission and validation work.
 *
 * Ballots are the canonical source of truth in ModNVote 2.0.
 * This service performs authoritative server-side validation and commits
 * ballots atomically together with ordered preferences and audit events.
 */
public final class BallotService {

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;
    private final BallotDao ballotDao;
    private final BallotPreferenceDao ballotPreferenceDao;
    private final AuditEventDao auditEventDao;

    public BallotService(DatabaseManager databaseManager,
                         PlatformAdapter platformAdapter,
                         Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollDao = new PollDao(databaseManager);
        this.pollOptionDao = new PollOptionDao(databaseManager);
        this.ballotDao = new BallotDao(databaseManager);
        this.ballotPreferenceDao = new BallotPreferenceDao(databaseManager);
        this.auditEventDao = new AuditEventDao(databaseManager);
    }

    public boolean isInitialized() {
        return true;
    }

    public String getStatusSummary() {
        return "BallotService ready";
    }

    public SubmissionResult submitRankedBallot(long pollId,
                                               UUID voterUuid,
                                               String voterName,
                                               String identityKey,
                                               String identityType,
                                               String clientPlatform,
                                               List<Long> rankedOptionIds,
                                               String ipHash,
                                               String floodgateId) throws PollServiceException {
        Objects.requireNonNull(voterUuid, "voterUuid");
        requireNonBlank(voterName, "voterName");
        requireNonBlank(identityKey, "identityKey");
        requireNonBlank(identityType, "identityType");
        requireNonBlank(clientPlatform, "clientPlatform");
        Objects.requireNonNull(rankedOptionIds, "rankedOptionIds");

        if (rankedOptionIds.isEmpty()) {
            throw new PollServiceException("At least one ranked option must be submitted.");
        }

        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            if (poll.status() != PollStatus.OPEN) {
                throw new PollServiceException("Poll #" + pollId + " is not OPEN.");
            }
            if (poll.pollType() != PollType.RANKED_SINGLE_WINNER) {
                throw new PollServiceException("Poll #" + pollId + " is not a ranked single-winner poll.");
            }

            List<PollOption> availableOptions = pollOptionDao.findOptionsByPollId(pollId);
            if (availableOptions.isEmpty()) {
                throw new PollServiceException("Poll #" + pollId + " has no selectable options.");
            }

            validateRankedSelection(poll, availableOptions, rankedOptionIds);

            Instant submittedAt = Instant.now();
            List<BallotPreference> preferences = toPreferences(rankedOptionIds);
            String canonicalBallotPayload = buildCanonicalBallotPayload(
                    poll,
                    voterUuid,
                    identityKey,
                    rankedOptionIds,
                    submittedAt
            );
            String ballotHash = sha256(canonicalBallotPayload);
            String receiptHash = sha256(ballotHash + "\n" + submittedAt.toEpochMilli());

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (ballotDao.existsBallotForPollAndVoterUuid(connection, pollId, voterUuid)) {
                        throw new PollServiceException("A ballot has already been submitted for this voter in poll #" + pollId + ".");
                    }

                    long ballotId = ballotDao.insertBallot(
                            connection,
                            pollId,
                            voterUuid,
                            voterName,
                            identityKey,
                            identityType,
                            ipHash,
                            floodgateId,
                            submittedAt,
                            clientPlatform,
                            ballotHash,
                            receiptHash,
                            true,
                            null
                    );

                    ballotPreferenceDao.insertPreferences(connection, ballotId, preferences);

                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "BALLOT_SUBMITTED",
                            buildAuditPayload(
                                    pollId,
                                    ballotId,
                                    voterUuid,
                                    identityKey,
                                    rankedOptionIds,
                                    ballotHash,
                                    receiptHash,
                                    submittedAt
                            )
                    );

                    connection.commit();

                    return new SubmissionResult(
                            ballotId,
                            ballotHash,
                            receiptHash,
                            submittedAt
                    );
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to submit ranked ballot for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to submit ranked ballot for poll #" + pollId, e);
        }
    }

    private void validateRankedSelection(Poll poll,
                                         List<PollOption> availableOptions,
                                         List<Long> rankedOptionIds) throws PollServiceException {
        Set<Long> validOptionIds = new HashSet<>();
        for (PollOption option : availableOptions) {
            validOptionIds.add(option.optionId());
        }

        Set<Long> seen = new LinkedHashSet<>();
        for (Long optionId : rankedOptionIds) {
            if (optionId == null) {
                throw new PollServiceException("Ranked selections must not contain null option ids.");
            }
            if (!validOptionIds.contains(optionId)) {
                throw new PollServiceException("Option #" + optionId + " does not belong to poll #" + poll.pollId() + ".");
            }
            if (!seen.add(optionId)) {
                throw new PollServiceException("Ranked selections must not contain duplicate option ids.");
            }
        }

        int availableCount = availableOptions.size();
        int maxRankings = poll.maxRankings() > 0
                ? Math.min(poll.maxRankings(), availableCount)
                : availableCount;

        if (rankedOptionIds.size() > maxRankings) {
            throw new PollServiceException("This poll allows at most " + maxRankings + " ranked selections.");
        }

        if (rankedOptionIds.isEmpty()) {
            throw new PollServiceException("At least one ranked selection is required.");
        }

        if (!poll.allowPartialRanking() && rankedOptionIds.size() != maxRankings) {
            throw new PollServiceException("This poll requires exactly " + maxRankings + " ranked selections.");
        }
    }

    private List<BallotPreference> toPreferences(List<Long> rankedOptionIds) {
        List<BallotPreference> preferences = new ArrayList<>();
        for (int i = 0; i < rankedOptionIds.size(); i++) {
            preferences.add(new BallotPreference(i + 1, rankedOptionIds.get(i)));
        }
        return preferences;
    }

    private String buildCanonicalBallotPayload(Poll poll,
                                               UUID voterUuid,
                                               String identityKey,
                                               List<Long> rankedOptionIds,
                                               Instant submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(poll.pollId()).append('\n');
        sb.append("poll_type=").append(poll.pollType().name()).append('\n');
        sb.append("voter_uuid=").append(voterUuid).append('\n');
        sb.append("identity_key=").append(identityKey).append('\n');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli()).append('\n');
        sb.append("max_rankings=").append(poll.maxRankings()).append('\n');
        sb.append("allow_partial_ranking=").append(poll.allowPartialRanking()).append('\n');
        sb.append("ordered_option_ids=").append(joinOptionIds(rankedOptionIds));
        return sb.toString();
    }

    private String buildAuditPayload(long pollId,
                                     long ballotId,
                                     UUID voterUuid,
                                     String identityKey,
                                     List<Long> rankedOptionIds,
                                     String ballotHash,
                                     String receiptHash,
                                     Instant submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(pollId).append(';');
        sb.append("ballot_id=").append(ballotId).append(';');
        sb.append("voter_uuid=").append(voterUuid).append(';');
        sb.append("identity_key=").append(identityKey).append(';');
        sb.append("ordered_option_ids=").append(joinOptionIds(rankedOptionIds)).append(';');
        sb.append("ballot_hash=").append(ballotHash).append(';');
        sb.append("receipt_hash=").append(receiptHash).append(';');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli());
        return sb.toString();
    }

    private String joinOptionIds(List<Long> rankedOptionIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rankedOptionIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(rankedOptionIds.get(i));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash ballot payload", e);
        }
    }

    private String requireNonBlank(String value, String fieldName) throws PollServiceException {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new PollServiceException(fieldName + " must not be blank.");
        }
        return value;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }

    public Logger getLogger() {
        return logger;
    }

    public record SubmissionResult(
            long ballotId,
            String ballotHash,
            String receiptHash,
            Instant submittedAt
    ) {
    }
}