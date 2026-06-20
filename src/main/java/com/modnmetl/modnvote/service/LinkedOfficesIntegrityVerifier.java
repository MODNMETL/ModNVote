package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.service.IntegrityVerificationService.IntegrityVerificationResult;
import com.modnmetl.modnvote.service.canonical.BallotCanonicalizer;
import com.modnmetl.modnvote.service.canonical.BallotHashingService;
import com.modnmetl.modnvote.storage.AnonymousBallotContestResponseDao;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AuditEventDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.ParticipationRecordDao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Recount/integrity verification for LINKED_OFFICES polls.
 *
 * <p>Linked-offices anonymous ballots store their multi-contest vote content in
 * {@code anonymous_ballot_contest_responses}. This verifier rebuilds each ballot
 * from those rows ({@link LinkedBallotReconstructor}), re-canonicalises it with
 * the shared {@link BallotCanonicalizer}, recomputes the ballot hash with the
 * shared {@link BallotHashingService}, and compares it to the stored hash — so an
 * offline edit to stored vote content cannot pass as valid.
 *
 * <p><strong>Why a standalone, Bukkit-free collaborator:</strong> like
 * {@link LinkedBallotStorageService}, this holds only a {@link DatabaseManager}
 * and never touches the platform layer, so it is fully unit-testable. The
 * single-contest integrity path in {@link IntegrityVerificationService} is
 * unchanged; that service simply delegates the LINKED_OFFICES case here.
 *
 * <p><strong>Scope:</strong> only {@code ballot_hash} is recomputed.
 * {@code ballot_commitment_hash} binds the voter's proof phrase, which integrity
 * verification does not possess, so commitment recomputation is deliberately left
 * to the bearer-token proof path. This is verification only: no counting,
 * tallying, result calculation, or dependency-outcome application. Failure
 * messages carry office/candidate keys and hashes but never voter identity.
 */
public final class LinkedOfficesIntegrityVerifier {

    private final ParticipationRecordDao participationRecordDao;
    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotContestResponseDao anonymousBallotContestResponseDao;
    private final AuditEventDao auditEventDao;
    private final BallotCanonicalizer ballotCanonicalizer;
    private final ElectionDefinitionService electionDefinitionService;
    private final LinkedBallotReconstructor linkedBallotReconstructor;

    public LinkedOfficesIntegrityVerifier(DatabaseManager databaseManager) {
        Objects.requireNonNull(databaseManager, "databaseManager");
        this.participationRecordDao = new ParticipationRecordDao(databaseManager);
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotContestResponseDao = new AnonymousBallotContestResponseDao(databaseManager);
        this.auditEventDao = new AuditEventDao(databaseManager);
        this.ballotCanonicalizer = new BallotCanonicalizer();
        this.electionDefinitionService = new ElectionDefinitionService();
        this.linkedBallotReconstructor = new LinkedBallotReconstructor();
    }

    /**
     * Verifies all anonymous ballots for a linked-offices poll. The election
     * definition is parsed and validated from {@code config_json}; each anonymous
     * ballot is rebuilt from its stored contest-response rows, re-canonicalised,
     * and its recomputed ballot hash compared to the stored hash.
     */
    public IntegrityVerificationResult verify(Poll poll) throws Exception {
        Objects.requireNonNull(poll, "poll");
        long pollId = poll.pollId();
        List<String> issues = new ArrayList<>();

        List<AnonymousBallotDao.StoredAnonymousBallot> ballots =
                anonymousBallotDao.findAnonymousBallotsByPollId(pollId);

        int participationRecordCount =
                participationRecordDao.findParticipationReceiptHashesByPollId(pollId).size();
        int anonymousBallotCount = ballots.size();

        boolean auditChainValid = auditEventDao.isPollAuditChainValid(pollId);
        boolean recordCountsMatch = participationRecordCount == anonymousBallotCount;

        boolean ballotHashesValid = true;

        ElectionDefinitionService.ElectionDefinitionValidationResult definitionResult =
                electionDefinitionService.validate(poll);

        if (definitionResult.definition().isEmpty() || !definitionResult.valid()) {
            // Without a valid definition no linked-offices ballot can be verified.
            if (!ballots.isEmpty()) {
                ballotHashesValid = false;
            }
            issues.add("Poll #" + pollId
                    + " has an invalid or missing linked-offices definition in config_json: "
                    + String.join("; ", definitionResult.issues()));
        } else {
            ElectionDefinition definition = definitionResult.definition().get();
            Set<String> seenBallotProofHashes = new HashSet<>();

            for (AnonymousBallotDao.StoredAnonymousBallot ballot : ballots) {
                if (ballot.ballotProofHash() == null || ballot.ballotProofHash().isBlank()) {
                    ballotHashesValid = false;
                    issues.add("Poll #" + pollId + " anonymous ballot #" + ballot.anonymousBallotId()
                            + " is missing ballot proof verifier material.");
                    continue;
                }
                if (!seenBallotProofHashes.add(ballot.ballotProofHash())) {
                    ballotHashesValid = false;
                    issues.add("Poll #" + pollId + " anonymous ballot proof hash collision detected.");
                }
                if (ballot.ballotCommitmentHash() == null || ballot.ballotCommitmentHash().isBlank()) {
                    ballotHashesValid = false;
                    issues.add("Poll #" + pollId + " anonymous ballot #" + ballot.anonymousBallotId()
                            + " is missing ballot commitment material.");
                }

                if (!verifyBallot(poll, definition, ballot, issues)) {
                    ballotHashesValid = false;
                }
            }
        }

        if (!recordCountsMatch) {
            issues.add("Poll #" + pollId
                    + " participation record count and anonymous ballot count do not match.");
        }

        boolean overallValid = auditChainValid && ballotHashesValid && recordCountsMatch;

        return new IntegrityVerificationResult(
                pollId,
                auditChainValid,
                ballotHashesValid,
                recordCountsMatch,
                overallValid,
                List.copyOf(issues)
        );
    }

    /**
     * Verifies a single linked-offices anonymous ballot. Returns {@code true} when
     * the recomputed ballot hash matches the stored hash; otherwise appends a
     * deterministic, identity-free issue and returns {@code false}.
     */
    private boolean verifyBallot(Poll poll,
                                 ElectionDefinition definition,
                                 AnonymousBallotDao.StoredAnonymousBallot ballot,
                                 List<String> issues) throws Exception {
        long pollId = poll.pollId();
        long ballotId = ballot.anonymousBallotId();

        List<AnonymousBallotContestResponse> rows =
                anonymousBallotContestResponseDao.findResponsesByAnonymousBallotId(ballotId);

        if (rows.isEmpty()) {
            issues.add("Poll #" + pollId + " anonymous ballot #" + ballotId
                    + " has no contest-response rows; cannot reconstruct linked-offices ballot.");
            return false;
        }

        String recomputedHash;
        try {
            LinkedElectionBallot reconstructed = linkedBallotReconstructor.reconstruct(definition, rows);
            // Re-canonicalisation re-validates the reconstructed ballot against the
            // definition (unknown office/candidate, ineligible candidate, etc.) and
            // throws IllegalArgumentException if the stored content is not valid.
            String payload = ballotCanonicalizer.canonicalLinkedOfficesBallotPayload(
                    poll, definition, reconstructed, ballot.submittedAt());
            recomputedHash = BallotHashingService.sha256(payload);
        } catch (LinkedBallotReconstructionException e) {
            issues.add("Poll #" + pollId + " anonymous ballot #" + ballotId
                    + " could not be reconstructed from stored rows: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            issues.add("Poll #" + pollId + " anonymous ballot #" + ballotId
                    + " failed linked-offices validation during recount: " + e.getMessage());
            return false;
        }

        if (!recomputedHash.equals(ballot.ballotHash())) {
            issues.add("Poll #" + pollId + " anonymous ballot #" + ballotId
                    + " failed linked-offices ballot hash verification (expected=" + recomputedHash
                    + ", actual=" + ballot.ballotHash() + ").");
            return false;
        }
        return true;
    }
}
