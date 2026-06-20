package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult.OfficeResponse;
import com.modnmetl.modnvote.service.canonical.BallotCanonicalizer;
import com.modnmetl.modnvote.service.canonical.BallotHashingService;
import com.modnmetl.modnvote.storage.AnonymousBallotContestResponseDao;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bearer-token proof verification for LINKED_OFFICES anonymous ballots.
 *
 * <p>This completes the linked-offices audit/proof loop: a proof phrase is hashed
 * to a {@code ballot_proof_hash}, the matching anonymous ballot is loaded, its
 * stored contest-response rows are rebuilt into a {@link LinkedElectionBallot}
 * ({@link LinkedBallotReconstructor}), re-canonicalised with the shared
 * {@link BallotCanonicalizer}, and the recomputed {@code ballot_hash} and
 * {@code ballot_commitment_hash} are compared to the stored values. A match
 * proves the held phrase corresponds to that exact stored ballot.
 *
 * <p><strong>Why a standalone, Bukkit-free collaborator:</strong> like
 * {@link LinkedBallotStorageService} and {@link LinkedOfficesIntegrityVerifier},
 * this holds only a {@link DatabaseManager} and never touches the platform layer,
 * so it is fully unit-testable. {@link BallotService} (which requires a Bukkit
 * {@code PlatformAdapter}) simply delegates the LINKED_OFFICES case here; the
 * single-contest proof path in {@code BallotService.verifyBallotProof} is
 * unchanged.
 *
 * <p><strong>Privacy:</strong> this reads only anonymous content keyed by
 * {@code anonymous_ballot_id} and never joins {@code participation_records}. The
 * returned result and every failure reason carry office/candidate keys and hashes
 * but no voter identity. Because proof verification is bearer-token based, the
 * holder of the phrase legitimately sees the anonymous ballot content on success.
 *
 * <p><strong>Scope:</strong> verification only — no counting, IRV/approval
 * tallying, result calculation, or dependency-outcome application.
 */
public final class LinkedOfficesProofVerifier {

    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotContestResponseDao anonymousBallotContestResponseDao;
    private final BallotCanonicalizer ballotCanonicalizer;
    private final ElectionDefinitionService electionDefinitionService;
    private final LinkedBallotReconstructor linkedBallotReconstructor;

    public LinkedOfficesProofVerifier(DatabaseManager databaseManager) {
        Objects.requireNonNull(databaseManager, "databaseManager");
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotContestResponseDao = new AnonymousBallotContestResponseDao(databaseManager);
        this.ballotCanonicalizer = new BallotCanonicalizer();
        this.electionDefinitionService = new ElectionDefinitionService();
        this.linkedBallotReconstructor = new LinkedBallotReconstructor();
    }

    /**
     * Verifies a proof phrase against the stored linked-offices anonymous ballot it
     * commits to. Returns a result exposing anonymous content only; never throws on
     * a verification miss (not-found / mismatch / corruption are reported in the
     * result), and never reveals voter identity.
     */
    public LinkedOfficeBallotProofVerificationResult verify(Poll poll, String ballotProofPhrase) throws Exception {
        Objects.requireNonNull(poll, "poll");
        if (ballotProofPhrase == null || ballotProofPhrase.isBlank()) {
            throw new IllegalArgumentException("ballotProofPhrase must not be blank.");
        }
        long pollId = poll.pollId();

        if (poll.pollType() != PollType.LINKED_OFFICES) {
            throw new IllegalArgumentException(
                    "Poll #" + pollId + " is not a LINKED_OFFICES poll.");
        }

        String ballotProofHash = BallotHashingService.buildBallotProofHash(pollId, ballotProofPhrase);
        AnonymousBallotDao.StoredAnonymousBallot ballot =
                anonymousBallotDao.findAnonymousBallotByPollIdAndProofHash(pollId, ballotProofHash);

        if (ballot == null) {
            // No content is revealed when nothing matches the bearer token.
            return notFound(pollId);
        }

        ElectionDefinitionService.ElectionDefinitionValidationResult definitionResult =
                electionDefinitionService.validate(poll);
        if (definitionResult.definition().isEmpty() || !definitionResult.valid()) {
            return failure(pollId, ballot, "Poll #" + pollId
                    + " has an invalid or missing linked-offices definition in config_json; "
                    + "the matched ballot cannot be verified.");
        }
        ElectionDefinition definition = definitionResult.definition().get();

        List<AnonymousBallotContestResponse> rows =
                anonymousBallotContestResponseDao.findResponsesByAnonymousBallotId(ballot.anonymousBallotId());
        if (rows.isEmpty()) {
            return failure(pollId, ballot, "Poll #" + pollId + " anonymous ballot #" + ballot.anonymousBallotId()
                    + " has no contest-response rows; cannot reconstruct linked-offices ballot.");
        }

        LinkedElectionBallot reconstructed;
        String canonicalPayload;
        try {
            reconstructed = linkedBallotReconstructor.reconstruct(definition, rows);
            // Re-canonicalisation re-validates the reconstructed ballot against the
            // definition and throws IllegalArgumentException if the stored content
            // is not a valid linked-offices ballot.
            canonicalPayload = ballotCanonicalizer.canonicalLinkedOfficesBallotPayload(
                    poll, definition, reconstructed, ballot.submittedAt());
        } catch (LinkedBallotReconstructionException e) {
            return failure(pollId, ballot, "Poll #" + pollId + " anonymous ballot #" + ballot.anonymousBallotId()
                    + " could not be reconstructed from stored rows: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return failure(pollId, ballot, "Poll #" + pollId + " anonymous ballot #" + ballot.anonymousBallotId()
                    + " failed linked-offices validation during proof verification: " + e.getMessage());
        }

        String recomputedBallotHash = BallotHashingService.sha256(canonicalPayload);
        boolean ballotHashValid = recomputedBallotHash.equals(ballot.ballotHash());

        String recomputedCommitmentHash =
                BallotHashingService.buildBallotCommitmentHash(ballotProofPhrase, canonicalPayload);
        boolean commitmentValid = recomputedCommitmentHash.equals(ballot.ballotCommitmentHash());

        if (!ballotHashValid || !commitmentValid) {
            // The phrase matched a stored ballot, but the stored content no longer
            // hashes/commits to the recorded values — corruption or tampering.
            return failure(pollId, ballot, "Poll #" + pollId + " anonymous ballot #" + ballot.anonymousBallotId()
                    + " matched the proof phrase but failed exact-ballot verification ("
                    + "ballot_hash_valid=" + ballotHashValid
                    + ", commitment_valid=" + commitmentValid + ").");
        }

        return new LinkedOfficeBallotProofVerificationResult(
                pollId,
                true,
                true,
                ballot.anonymousBallotId(),
                ballot.ballotHash(),
                ballot.submittedAt(),
                toOfficeResponses(definition, reconstructed),
                null
        );
    }

    /**
     * Projects the reconstructed ballot into per-office anonymous content, iterating
     * offices in election-definition order so the output is deterministic.
     */
    private static List<OfficeResponse> toOfficeResponses(ElectionDefinition definition,
                                                          LinkedElectionBallot ballot) {
        List<OfficeResponse> offices = new ArrayList<>();
        for (ContestDefinition contest : definition.contests()) {
            Optional<ContestVote> response = ballot.findResponse(contest.officeKey());
            if (response.isEmpty()) {
                continue;
            }
            ContestVote vote = response.get();
            if (vote instanceof RankedContestVote ranked) {
                offices.add(new OfficeResponse(
                        contest.officeKey(),
                        AnonymousBallotContestResponse.TYPE_RANKED,
                        ranked.orderedCandidateKeys()));
            } else if (vote instanceof ApprovalContestVote approval) {
                offices.add(new OfficeResponse(
                        contest.officeKey(),
                        AnonymousBallotContestResponse.TYPE_APPROVAL,
                        approval.selectedCandidateKeys()));
            }
        }
        return offices;
    }

    private static LinkedOfficeBallotProofVerificationResult notFound(long pollId) {
        return new LinkedOfficeBallotProofVerificationResult(
                pollId, false, false, null, null, null, List.of(), null);
    }

    private static LinkedOfficeBallotProofVerificationResult failure(long pollId,
                                                                    AnonymousBallotDao.StoredAnonymousBallot ballot,
                                                                    String reason) {
        return new LinkedOfficeBallotProofVerificationResult(
                pollId,
                true,
                false,
                ballot.anonymousBallotId(),
                ballot.ballotHash(),
                ballot.submittedAt(),
                List.of(),
                reason);
    }
}
