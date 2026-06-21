package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.BallotPreference;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.service.canonical.BallotCanonicalizer;
import com.modnmetl.modnvote.service.canonical.BallotHashingService;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AnonymousBallotPreferenceDao;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.ParticipationRecordDao;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import java.security.SecureRandom;
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
 * Privacy-hardened model:
 * - participation tracking remains identity-aware but vote-content-blind
 * - anonymous ballots remain content-bearing but identity-free
 * - no shared receipt linkage is persisted across those layers
 *
 * Player-facing verification is split conceptually into:
 * - participation verification (identity-aware, no vote content)
 * - ballot-proof verification (identity-free, exact ballot confirmation)
 */
public final class BallotService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Shared proof-phrase generator. The curated word list and the four-word
     * generation logic now live in {@link BallotProofPhraseGenerator} so this
     * single-contest path and the linked-offices submission path cannot drift on
     * proof-phrase semantics.
     */
    private final BallotProofPhraseGenerator ballotProofPhraseGenerator = new BallotProofPhraseGenerator();

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;
    private final ParticipationRecordDao participationRecordDao;
    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotPreferenceDao anonymousBallotPreferenceDao;
    private final AuditEventDao auditEventDao;
    private final BallotCanonicalizer ballotCanonicalizer;
    private final LinkedOfficesProofVerifier linkedOfficesProofVerifier;

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
        this.ballotCanonicalizer = new BallotCanonicalizer();
        this.linkedOfficesProofVerifier = new LinkedOfficesProofVerifier(databaseManager);
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
                                               String floodgateId,
                                               boolean bypassIpDuplicateCheck) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");
        requireNonBlank(clientPlatform, "clientPlatform");
        Objects.requireNonNull(rankedOptionIds, "rankedOptionIds");

        if (rankedOptionIds.isEmpty()) {
            throw new PollServiceException("At least one ranked option must be submitted.");
        }

        try {
            Poll poll = requireOpenPollOfType(pollId, PollType.RANKED_SINGLE_WINNER);
            List<PollOption> availableOptions = requireSelectableOptions(pollId);

            validateRankedSelection(poll, availableOptions, rankedOptionIds);

            return submitOrderedBallot(
                    poll,
                    identityKey,
                    clientPlatform,
                    rankedOptionIds,
                    ipHash,
                    floodgateId,
                    bypassIpDuplicateCheck
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to submit ranked ballot for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to submit ranked ballot for poll #" + pollId, e);
        }
    }

    public SubmissionResult submitYesNoBallot(long pollId,
                                              String identityKey,
                                              String clientPlatform,
                                              long selectedOptionId,
                                              String ipHash,
                                              String floodgateId,
                                              boolean bypassIpDuplicateCheck) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");
        requireNonBlank(clientPlatform, "clientPlatform");

        try {
            Poll poll = requireOpenPollOfType(pollId, PollType.YES_NO);
            List<PollOption> availableOptions = requireSelectableOptions(pollId);

            validateYesNoSelection(poll, availableOptions, selectedOptionId);

            return submitOrderedBallot(
                    poll,
                    identityKey,
                    clientPlatform,
                    List.of(selectedOptionId),
                    ipHash,
                    floodgateId,
                    bypassIpDuplicateCheck
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to submit yes/no ballot for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to submit yes/no ballot for poll #" + pollId, e);
        }
    }

    /**
     * Identity-aware verification that confirms participation without revealing
     * ballot content.
     *
     * Compatibility note:
     * The existing command layer still expects a field named
     * receiptBackedByAnonymousBallot. In the hardened model there is
     * intentionally no persistent bridge that can prove this per-voter without
     * reintroducing linkability. For now this compatibility flag is true when
     * participation exists, reflecting the transactional submission guarantee.
     * The command UX will be redesigned in the next batch.
     */
    public VerificationResult verifyVoterInclusion(long pollId, String identityKey) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");

        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }

            String participationTokenHash = deriveParticipationTokenHash(poll, identityKey);
            String storedParticipationReceiptHash =
                    participationRecordDao.findParticipationReceiptHashByPollAndTokenHash(pollId, participationTokenHash);

            boolean included = storedParticipationReceiptHash != null;
            boolean auditChainValid = auditEventDao.isPollAuditChainValid(pollId);

            return new VerificationResult(
                    pollId,
                    included,
                    included,
                    auditChainValid,
                    storedParticipationReceiptHash
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to verify voter inclusion for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to verify voter inclusion for poll #" + pollId, e);
        }
    }

    /**
     * Identity-free ballot proof verification.
     *
     * A user presents the private ballot proof phrase that was shown at
     * submission time. If the phrase matches a stored anonymous ballot in this
     * poll, the service verifies:
     * - the ballot still exists
     * - the canonical ballot hash still matches stored preferences
     * - the ballot proof commitment still matches
     *
     * This reveals the ballot content but does not use the participation layer.
     */
    public BallotProofVerificationResult verifyBallotProof(long pollId,
                                                           String ballotProofPhrase) throws PollServiceException {
        requireNonBlank(ballotProofPhrase, "ballotProofPhrase");

        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }

            String ballotProofHash = buildBallotProofHash(pollId, ballotProofPhrase);
            AnonymousBallotDao.StoredAnonymousBallot ballot =
                    anonymousBallotDao.findAnonymousBallotByPollIdAndProofHash(pollId, ballotProofHash);

            if (ballot == null) {
                return new BallotProofVerificationResult(
                        pollId,
                        false,
                        false,
                        false,
                        null,
                        null,
                        List.of()
                );
            }

            List<AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference> preferences =
                    anonymousBallotPreferenceDao.findPreferencesByAnonymousBallotId(ballot.anonymousBallotId());

            if (preferences.isEmpty()) {
                return new BallotProofVerificationResult(
                        pollId,
                        true,
                        false,
                        false,
                        ballot.anonymousBallotId(),
                        ballot.ballotHash(),
                        List.of()
                );
            }

            List<Long> orderedOptionIds = new ArrayList<>(preferences.size());
            for (int i = 0; i < preferences.size(); i++) {
                AnonymousBallotPreferenceDao.StoredAnonymousBallotPreference preference = preferences.get(i);
                int expectedRank = i + 1;
                if (preference.rankPosition() != expectedRank) {
                    return new BallotProofVerificationResult(
                            pollId,
                            true,
                            false,
                            false,
                            ballot.anonymousBallotId(),
                            ballot.ballotHash(),
                            List.copyOf(orderedOptionIds)
                    );
                }
                orderedOptionIds.add(preference.optionId());
            }

            String canonicalAnonymousBallotPayload = ballotCanonicalizer.canonicalAnonymousBallotPayload(
                    poll,
                    orderedOptionIds,
                    ballot.submittedAt()
            );

            String recomputedBallotHash = sha256(canonicalAnonymousBallotPayload);
            boolean ballotHashValid = recomputedBallotHash.equals(ballot.ballotHash());

            String recomputedCommitmentHash = buildBallotCommitmentHash(
                    ballotProofPhrase,
                    canonicalAnonymousBallotPayload
            );
            boolean commitmentValid = recomputedCommitmentHash.equals(ballot.ballotCommitmentHash());

            return new BallotProofVerificationResult(
                    pollId,
                    true,
                    ballotHashValid,
                    commitmentValid,
                    ballot.anonymousBallotId(),
                    ballot.ballotHash(),
                    List.copyOf(orderedOptionIds)
            );
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to verify ballot proof for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to verify ballot proof for poll #" + pollId, e);
        }
    }

    /**
     * Identity-free bearer-token proof verification for LINKED_OFFICES ballots.
     *
     * <p>The single-contest {@link #verifyBallotProof} path (YES_NO /
     * RANKED_SINGLE_WINNER) is deliberately left unchanged: it cannot represent
     * multi-contest content, so linked-offices proof verification has its own entry
     * point and its own {@link LinkedOfficeBallotProofVerificationResult}.
     *
     * <p>The real work lives in the standalone, Bukkit-free
     * {@link LinkedOfficesProofVerifier}; this method only resolves and type-checks
     * the poll and delegates. It reveals anonymous ballot content to the holder of
     * the proof phrase but never touches the participation layer or voter identity.
     */
    public LinkedOfficeBallotProofVerificationResult verifyLinkedOfficeBallotProof(long pollId,
                                                                                  String ballotProofPhrase)
            throws PollServiceException {
        requireNonBlank(ballotProofPhrase, "ballotProofPhrase");

        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            if (poll.pollType() != PollType.LINKED_OFFICES) {
                throw new PollServiceException("Poll #" + pollId + " is not a LINKED_OFFICES poll.");
            }
            return linkedOfficesProofVerifier.verify(poll, ballotProofPhrase);
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Failed to verify linked-offices ballot proof for poll #" + pollId + ": " + e.getMessage());
            throw new PollServiceException("Failed to verify ballot proof for poll #" + pollId, e);
        }
    }

    public List<ParticipationSummary> listParticipatedPolls(String identityKey) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");

        try {
            List<Poll> polls = pollDao.findAllPolls();
            List<ParticipationSummary> results = new ArrayList<>();

            for (Poll poll : polls) {
                String participationTokenHash = deriveParticipationTokenHash(poll, identityKey);
                String participationReceiptHash =
                        participationRecordDao.findParticipationReceiptHashByPollAndTokenHash(
                                poll.pollId(),
                                participationTokenHash
                        );

                if (participationReceiptHash != null) {
                    results.add(new ParticipationSummary(
                            poll.pollId(),
                            poll.title(),
                            poll.status().name()
                    ));
                }
            }

            return List.copyOf(results);
        } catch (Exception e) {
            logger.warning("Failed to list participated polls: " + e.getMessage());
            throw new PollServiceException("Failed to list participated polls.", e);
        }
    }

    private Poll requireOpenPollOfType(long pollId, PollType expectedType) throws PollServiceException {
        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            if (poll.status() != PollStatus.OPEN) {
                throw new PollServiceException("Poll #" + pollId + " is not OPEN.");
            }
            if (poll.pollType() != expectedType) {
                throw new PollServiceException("Poll #" + pollId + " is not a " + expectedType.name() + " poll.");
            }
            return poll;
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to load poll #" + pollId, e);
        }
    }

    private List<PollOption> requireSelectableOptions(long pollId) throws PollServiceException {
        try {
            List<PollOption> availableOptions = pollOptionDao.findOptionsByPollId(pollId);
            if (availableOptions.isEmpty()) {
                throw new PollServiceException("Poll #" + pollId + " has no selectable options.");
            }
            return availableOptions;
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to load options for poll #" + pollId, e);
        }
    }

    private SubmissionResult submitOrderedBallot(Poll poll,
                                                 String identityKey,
                                                 String clientPlatform,
                                                 List<Long> orderedOptionIds,
                                                 String ipHash,
                                                 String floodgateId,
                                                 boolean bypassIpDuplicateCheck) throws Exception {
        Instant submittedAt = Instant.now();
        String participationTokenHash = deriveParticipationTokenHash(poll, identityKey);
        List<BallotPreference> preferences = toPreferences(orderedOptionIds);

        String canonicalAnonymousBallotPayload = ballotCanonicalizer.canonicalAnonymousBallotPayload(
                poll,
                orderedOptionIds,
                submittedAt
        );

        String ballotHash = sha256(canonicalAnonymousBallotPayload);

        String participationReceipt = generateOpaqueReceipt();
        String participationReceiptHash = buildParticipationReceiptHash(
                poll.pollId(),
                participationReceipt
        );

        String ballotProofPhrase = generateBallotProofPhrase();
        String ballotProofHash = buildBallotProofHash(
                poll.pollId(),
                ballotProofPhrase
        );
        String ballotCommitmentHash = buildBallotCommitmentHash(
                ballotProofPhrase,
                canonicalAnonymousBallotPayload
        );

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (participationRecordDao.existsParticipationForPollAndTokenHash(
                        connection,
                        poll.pollId(),
                        participationTokenHash
                )) {
                    throw new PollServiceException("A vote has already been recorded for this participant in poll #" + poll.pollId() + ".");
                }

                if (!bypassIpDuplicateCheck && ipHash != null
                        && participationRecordDao.existsParticipationForPollAndIpHash(connection, poll.pollId(), ipHash)) {
                    throw new PollServiceException("A vote from your location has already been recorded for poll #" + poll.pollId() + ".");
                }

                participationRecordDao.insertParticipationRecord(
                        connection,
                        poll.pollId(),
                        participationTokenHash,
                        submittedAt,
                        participationReceiptHash,
                        clientPlatform,
                        ipHash,
                        floodgateId
                );

                long anonymousBallotId = anonymousBallotDao.insertAnonymousBallot(
                        connection,
                        poll.pollId(),
                        ballotHash,
                        ballotProofHash,
                        ballotCommitmentHash,
                        submittedAt
                );

                anonymousBallotPreferenceDao.insertPreferences(connection, anonymousBallotId, preferences);

                auditEventDao.insertPollEvent(
                        connection,
                        poll.pollId(),
                        "BALLOT_SUBMITTED",
                        buildAuditPayload(
                                poll.pollId(),
                                anonymousBallotId,
                                ballotHash,
                                submittedAt
                        )
                );

                connection.commit();

                return new SubmissionResult(
                        anonymousBallotId,
                        ballotHash,
                        participationReceipt,
                        ballotProofPhrase,
                        submittedAt
                );
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
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

    private void validateYesNoSelection(Poll poll,
                                        List<PollOption> availableOptions,
                                        long selectedOptionId) throws PollServiceException {
        if (availableOptions.size() != 2) {
            throw new PollServiceException("YES_NO poll #" + poll.pollId() + " must have exactly 2 selectable options.");
        }

        boolean valid = false;
        for (PollOption option : availableOptions) {
            if (option.optionId() == selectedOptionId) {
                valid = true;
                break;
            }
        }

        if (!valid) {
            throw new PollServiceException("Option #" + selectedOptionId + " does not belong to poll #" + poll.pollId() + ".");
        }
    }

    private List<BallotPreference> toPreferences(List<Long> orderedOptionIds) {
        List<BallotPreference> preferences = new ArrayList<>();
        for (int i = 0; i < orderedOptionIds.size(); i++) {
            preferences.add(new BallotPreference(i + 1, orderedOptionIds.get(i)));
        }
        return preferences;
    }

    private String deriveParticipationTokenHash(Poll poll, String identityKey) {
        return BallotHashingService.deriveParticipationTokenHash(poll, identityKey);
    }

    private String buildAuditPayload(long pollId,
                                     long anonymousBallotId,
                                     String ballotHash,
                                     Instant submittedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(pollId).append(';');
        sb.append("anonymous_ballot_id=").append(anonymousBallotId).append(';');
        sb.append("ballot_hash=").append(ballotHash).append(';');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli());
        return sb.toString();
    }

    private String buildParticipationReceiptHash(long pollId,
                                                 String participationReceipt) {
        return BallotHashingService.buildParticipationReceiptHash(pollId, participationReceipt);
    }

    private String buildBallotProofHash(long pollId,
                                        String ballotProofPhrase) {
        return BallotHashingService.buildBallotProofHash(pollId, ballotProofPhrase);
    }

    private String buildBallotCommitmentHash(String ballotProofPhrase,
                                             String canonicalAnonymousBallotPayload) {
        return BallotHashingService.buildBallotCommitmentHash(ballotProofPhrase, canonicalAnonymousBallotPayload);
    }

    private String generateOpaqueReceipt() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String generateBallotProofPhrase() {
        return ballotProofPhraseGenerator.generate();
    }

    private String sha256(String input) {
        return BallotHashingService.sha256(input);
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
            String participationReceipt,
            String ballotProofPhrase,
            Instant submittedAt
    ) {
        public SubmissionResult {
            Objects.requireNonNull(ballotHash, "ballotHash");
            Objects.requireNonNull(participationReceipt, "participationReceipt");
            Objects.requireNonNull(ballotProofPhrase, "ballotProofPhrase");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        /**
         * Backward-compatibility bridge for current listener code.
         * This now returns the participation receipt value shown to the player.
         */
        public String receiptHash() {
            return participationReceipt;
        }
    }

    public record VerificationResult(
            long pollId,
            boolean included,
            boolean receiptBackedByAnonymousBallot,
            boolean auditChainValid,
            String receiptHash
    ) {
    }

    public record ParticipationSummary(
            long pollId,
            String pollTitle,
            String pollStatus
    ) {
        public ParticipationSummary {
            Objects.requireNonNull(pollTitle, "pollTitle");
            Objects.requireNonNull(pollStatus, "pollStatus");
        }
    }

    public record BallotProofVerificationResult(
            long pollId,
            boolean ballotFound,
            boolean ballotHashValid,
            boolean commitmentValid,
            Long anonymousBallotId,
            String ballotHash,
            List<Long> orderedOptionIds
    ) {
        public BallotProofVerificationResult {
            Objects.requireNonNull(orderedOptionIds, "orderedOptionIds");
            orderedOptionIds = List.copyOf(orderedOptionIds);
        }

        public boolean overallValid() {
            return ballotFound && ballotHashValid && commitmentValid;
        }
    }
}
