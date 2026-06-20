package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.CanonicalBallot;
import com.modnmetl.modnvote.domain.election.execution.CanonicalContestResponse;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionCanonicalModel;
import com.modnmetl.modnvote.service.canonical.BallotCanonicalizer;
import com.modnmetl.modnvote.service.canonical.BallotHashingService;
import com.modnmetl.modnvote.storage.AnonymousBallotContestResponseDao;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.ParticipationRecordDao;

import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Stores anonymous, multi-contest vote content for LINKED_OFFICES ballots and
 * wires the Tranche 2F canonical payload into ballot-hash / commitment derivation.
 *
 * <p><strong>This is infrastructure, not a player-facing voting path.</strong>
 * It is deliberately Bukkit-free and is not reachable from any command, vote
 * session, or GUI. It implements no counting, tallying, or result calculation.
 * It exists so multi-contest anonymous ballot content can be landed and so the
 * hash input for linked-offices ballots is exercised end to end.
 *
 * <p>Privacy: this service writes exactly one participation record (identity-aware,
 * vote-content-blind) and exactly one anonymous ballot (content-bearing,
 * identity-free) per stored ballot, plus the anonymous contest-response rows. The
 * content rows link only to {@code anonymous_ballot_id} and carry no identity.
 * Hashing (participation token, proof, commitment) is delegated to the shared
 * {@link BallotHashingService}, the same helper {@link BallotService} uses, so the
 * two layers can never drift apart on hash derivation.
 *
 * <p>State requirement: as a storage primitive (not a real submission), this does
 * not require the poll to be {@code OPEN}; it only requires a {@code LINKED_OFFICES}
 * poll. A future genuine submission path must add the {@code OPEN} lifecycle check.
 */
public final class LinkedBallotStorageService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DatabaseManager databaseManager;
    private final ParticipationRecordDao participationRecordDao;
    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotContestResponseDao anonymousBallotContestResponseDao;
    private final AuditEventDao auditEventDao;
    private final BallotCanonicalizer ballotCanonicalizer;
    private final LinkedElectionCanonicalModel linkedCanonicalModel;

    public LinkedBallotStorageService(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.participationRecordDao = new ParticipationRecordDao(databaseManager);
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotContestResponseDao = new AnonymousBallotContestResponseDao(databaseManager);
        this.auditEventDao = new AuditEventDao(databaseManager);
        this.ballotCanonicalizer = new BallotCanonicalizer();
        this.linkedCanonicalModel = new LinkedElectionCanonicalModel();
    }

    /**
     * Validates and transactionally stores a linked-offices ballot.
     *
     * <p>Validation runs before any write: the canonical payload is produced via
     * {@link BallotCanonicalizer#canonicalLinkedOfficesBallotPayload}, which runs
     * {@code LinkedElectionBallotValidator} and rejects an invalid ballot (or a
     * definition that does not match the ballot) with an exception. An invalid
     * ballot therefore writes nothing. All inserts (participation record,
     * anonymous ballot, contest responses, audit event) succeed or roll back
     * together.
     *
     * @return a summary of what was stored (no identity material)
     * @throws PollServiceException if the poll is not LINKED_OFFICES or the
     *                              participant has already voted
     * @throws IllegalArgumentException if the ballot fails validation
     */
    public LinkedBallotStorageResult storeLinkedOfficesBallot(Poll poll,
                                                              ElectionDefinition definition,
                                                              LinkedElectionBallot ballot,
                                                              String identityKey,
                                                              String clientPlatform,
                                                              String ipHash,
                                                              String floodgateId,
                                                              String ballotProofPhrase,
                                                              Instant submittedAt) throws Exception {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(ballot, "ballot");
        requireNonBlank(identityKey, "identityKey");
        requireNonBlank(clientPlatform, "clientPlatform");
        requireNonBlank(ballotProofPhrase, "ballotProofPhrase");
        Objects.requireNonNull(submittedAt, "submittedAt");

        if (poll.pollType() != PollType.LINKED_OFFICES) {
            throw new PollServiceException("Poll #" + poll.pollId() + " is not a LINKED_OFFICES poll.");
        }

        // Validation gate: throws (before any DB write) for an invalid ballot, a
        // non-linked poll, or a definition that does not match the ballot.
        String canonicalPayload = ballotCanonicalizer.canonicalLinkedOfficesBallotPayload(
                poll, definition, ballot, submittedAt);

        // Rows are derived from the SAME canonical model the payload uses, so the
        // stored row order is exactly the hashed canonical order.
        CanonicalBallot canonicalBallot = linkedCanonicalModel.canonicalize(ballot);
        List<AnonymousBallotContestResponseDao.NewContestResponse> responseRows =
                toContestResponseRows(canonicalBallot);

        String ballotHash = BallotHashingService.sha256(canonicalPayload);

        String participationTokenHash = BallotHashingService.deriveParticipationTokenHash(poll, identityKey);
        String participationReceipt = generateOpaqueReceipt();
        String participationReceiptHash =
                BallotHashingService.buildParticipationReceiptHash(poll.pollId(), participationReceipt);

        String ballotProofHash = BallotHashingService.buildBallotProofHash(poll.pollId(), ballotProofPhrase);
        String ballotCommitmentHash =
                BallotHashingService.buildBallotCommitmentHash(ballotProofPhrase, canonicalPayload);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (participationRecordDao.existsParticipationForPollAndTokenHash(
                        connection, poll.pollId(), participationTokenHash)) {
                    throw new PollServiceException(
                            "A vote has already been recorded for this participant in poll #" + poll.pollId() + ".");
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

                anonymousBallotContestResponseDao.insertResponses(connection, anonymousBallotId, responseRows);

                auditEventDao.insertPollEvent(
                        connection,
                        poll.pollId(),
                        "LINKED_BALLOT_STORED",
                        buildAuditPayload(poll.pollId(), anonymousBallotId, ballotHash, submittedAt)
                );

                connection.commit();

                return new LinkedBallotStorageResult(
                        anonymousBallotId,
                        ballotHash,
                        ballotCommitmentHash,
                        participationReceipt,
                        canonicalPayload,
                        responseRows.size(),
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

    /**
     * Flattens a canonical ballot into ordered insert rows. Contests are emitted
     * in canonical (definition) order; within a contest, ranked candidates carry a
     * 1-based {@code rankPosition} and approval candidates a 1-based
     * {@code selectionOrder} in canonical candidate order.
     */
    private List<AnonymousBallotContestResponseDao.NewContestResponse> toContestResponseRows(
            CanonicalBallot canonicalBallot) {
        List<AnonymousBallotContestResponseDao.NewContestResponse> rows = new ArrayList<>();
        for (CanonicalContestResponse response : canonicalBallot.responses()) {
            boolean ranked = response.method() == CountingMethod.IRV;
            String responseType = ranked
                    ? AnonymousBallotContestResponse.TYPE_RANKED
                    : AnonymousBallotContestResponse.TYPE_APPROVAL;
            List<String> candidateKeys = response.orderedCandidateKeys();
            for (int i = 0; i < candidateKeys.size(); i++) {
                Integer rankPosition = ranked ? (i + 1) : null;
                Integer selectionOrder = ranked ? null : (i + 1);
                rows.add(new AnonymousBallotContestResponseDao.NewContestResponse(
                        response.officeKey(),
                        responseType,
                        candidateKeys.get(i),
                        rankPosition,
                        selectionOrder
                ));
            }
        }
        return rows;
    }

    // --- Audit payload + receipt generation -----------------------------------

    private String buildAuditPayload(long pollId, long anonymousBallotId, String ballotHash, Instant submittedAt) {
        return "poll_id=" + pollId + ';'
                + "anonymous_ballot_id=" + anonymousBallotId + ';'
                + "ballot_hash=" + ballotHash + ';'
                + "submitted_at=" + submittedAt.toEpochMilli();
    }

    private String generateOpaqueReceipt() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String requireNonBlank(String value, String fieldName) throws PollServiceException {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new PollServiceException(fieldName + " must not be blank.");
        }
        return value;
    }

    /**
     * Summary of a stored linked-offices ballot. Carries no identity material —
     * only anonymous content anchors (hashes), the participation receipt shown to
     * the voter, the canonical payload that was hashed, and the row count.
     */
    public record LinkedBallotStorageResult(
            long anonymousBallotId,
            String ballotHash,
            String ballotCommitmentHash,
            String participationReceipt,
            String canonicalPayload,
            int contestResponseRowCount,
            Instant submittedAt
    ) {
        public LinkedBallotStorageResult {
            Objects.requireNonNull(ballotHash, "ballotHash");
            Objects.requireNonNull(ballotCommitmentHash, "ballotCommitmentHash");
            Objects.requireNonNull(participationReceipt, "participationReceipt");
            Objects.requireNonNull(canonicalPayload, "canonicalPayload");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }
    }
}
