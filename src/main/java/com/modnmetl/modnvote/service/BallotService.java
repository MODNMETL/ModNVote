package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.BallotPreference;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AnonymousBallotPreferenceDao;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.ParticipationRecordDao;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.logging.Logger;

/**
 * Service layer for ballot submission and verification work.
 *
 * This privacy-preserving revision separates:
 * - participation tracking (identity-aware, no vote content)
 * - anonymous ballot storage (vote content, no identity)
 *
 * Anonymous ballots are the canonical recount source of truth.
 */
public final class BallotService {

    private static final String PARTICIPATION_TOKEN_ALGORITHM = "HmacSHA256";

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;
    private final ParticipationRecordDao participationRecordDao;
    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotPreferenceDao anonymousBallotPreferenceDao;
    private final AuditEventDao auditEventDao;

    public BallotService(DatabaseManager databaseManager,
                         PlatformAdapter platformAdapter,
                         Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollDao = new PollDao(databaseManager);
        this.pollOptionDao = new PollOptionDao(databaseManager);
        this.participationRecordDao = new ParticipationRecordDao(databaseManager);
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotPreferenceDao = new AnonymousBallotPreferenceDao(databaseManager);
        this.auditEventDao = new AuditEventDao(databaseManager);
    }

    public boolean isInitialized() {
        return true;
    }

    public String getStatusSummary() {
        return "BallotService ready";
    }

    public SubmissionResult submitRankedBallot(long pollId,
                                               String identityKey,
                                               String clientPlatform,
                                               List<Long> rankedOptionIds,
                                               String ipHash,
                                               String floodgateId) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");
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
            String participationTokenHash = deriveParticipationTokenHash(poll, identityKey);
            List<BallotPreference> preferences = toPreferences(rankedOptionIds);

            String canonicalAnonymousBallotPayload = buildCanonicalAnonymousBallotPayload(
                    poll,
                    rankedOptionIds,
                    submittedAt
            );
            String ballotHash = sha256(canonicalAnonymousBallotPayload);
            String receiptHash = sha256(participationTokenHash + "\n" + ballotHash + "\n" + submittedAt.toEpochMilli());

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (participationRecordDao.existsParticipationForPollAndTokenHash(
                            connection,
                            pollId,
                            participationTokenHash
                    )) {
                        throw new PollServiceException("A vote has already been recorded for this participant in poll #" + pollId + ".");
                    }

                    participationRecordDao.insertParticipationRecord(
                            connection,
                            pollId,
                            participationTokenHash,
                            submittedAt,
                            receiptHash,
                            clientPlatform,
                            ipHash,
                            floodgateId
                    );

                    long anonymousBallotId = anonymousBallotDao.insertAnonymousBallot(
                            connection,
                            pollId,
                            ballotHash,
                            receiptHash,
                            submittedAt
                    );

                    anonymousBallotPreferenceDao.insertPreferences(connection, anonymousBallotId, preferences);

                    auditEventDao.insertPollEvent(
                            connection,
                            pollId,
                            "BALLOT_SUBMITTED",
                            buildAuditPayload(
                                    pollId,
                                    anonymousBallotId,
                                    participationTokenHash,
                                    ballotHash,
                                    receiptHash,
                                    submittedAt
                            )
                    );

                    connection.commit();

                    return new SubmissionResult(
                            anonymousBallotId,
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

    public VerificationResult verifyVoterInclusion(long pollId, String identityKey) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");

        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }

            String participationTokenHash = deriveParticipationTokenHash(poll, identityKey);
            String receiptHash = participationRecordDao.findReceiptHashByPollAndTokenHash(pollId, participationTokenHash);
            boolean included = receiptHash != null;
            boolean receiptBackedByAnonymousBallot = included
                    && anonymousBallotDao.existsAnonymousBallotForPollAndReceiptHash(pollId, receiptHash);
            boolean auditChainValid = auditEventDao.isPollAuditChainValid(pollId);

            return new VerificationResult(
                    pollId,
                    included,
                    receiptBackedByAnonymousBallot,
                    auditChainValid,
                    receiptHash
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to verify voter inclusion for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to verify voter inclusion for poll #" + pollId, e);
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

    private String deriveParticipationTokenHash(Poll poll, String identityKey) {
        try {
            String input = poll.pollId() + "\n" + identityKey;
            Mac mac = Mac.getInstance(PARTICIPATION_TOKEN_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    poll.participationSecret().getBytes(StandardCharsets.UTF_8),
                    PARTICIPATION_TOKEN_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive participation token hash", e);
        }
    }

    private String buildCanonicalAnonymousBallotPayload(Poll poll,
                                                        List<Long> rankedOptionIds,
                                                        Instant submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(poll.pollId()).append('\n');
        sb.append("poll_type=").append(poll.pollType().name()).append('\n');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli()).append('\n');
        sb.append("rule_snapshot_version=v1").append('\n');
        sb.append("max_rankings=").append(poll.maxRankings()).append('\n');
        sb.append("allow_partial_ranking=").append(poll.allowPartialRanking()).append('\n');
        sb.append("ordered_option_ids=").append(joinOptionIds(rankedOptionIds));
        return sb.toString();
    }

    private String buildAuditPayload(long pollId,
                                     long anonymousBallotId,
                                     String participationTokenHash,
                                     String ballotHash,
                                     String receiptHash,
                                     Instant submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(pollId).append(';');
        sb.append("anonymous_ballot_id=").append(anonymousBallotId).append(';');
        sb.append("participation_token_hash=").append(participationTokenHash).append(';');
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
            long anonymousBallotId,
            String ballotHash,
            String receiptHash,
            Instant submittedAt
    ) {
    }

    public record VerificationResult(
            long pollId,
            boolean included,
            boolean receiptBackedByAnonymousBallot,
            boolean auditChainValid,
            String receiptHash
    ) {
    }
}