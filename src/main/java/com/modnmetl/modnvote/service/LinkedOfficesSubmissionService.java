package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.BallotValidationIssue;
import com.modnmetl.modnvote.domain.election.execution.BallotValidationResult;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallotValidator;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Player-facing submission path for LINKED_OFFICES ballots.
 *
 * <p>This is the genuine submission entry point the earlier
 * {@link LinkedBallotStorageService} infrastructure was waiting for: it adds the
 * lifecycle and validation gates a real vote requires, then delegates the actual
 * transactional write (one participation record, one anonymous ballot, the
 * contest-response rows) to the unchanged storage primitive.
 *
 * <p>It is deliberately Bukkit-free and holds only a {@link DatabaseManager} and
 * pure collaborators, so the whole submission flow — OPEN gate, definition
 * validation, ballot validation, proof-phrase issuance, duplicate prevention,
 * rollback — is unit-testable against a temporary database, the same pattern as
 * {@link LinkedOfficesIntegrityVerifier} and {@link LinkedElectionResultService}.
 *
 * <p>Privacy: voter identity ({@code identityKey}, {@code ipHash},
 * {@code floodgateId}) is used only to derive the participation token / record;
 * it never reaches the anonymous ballot content. The returned
 * {@link LinkedSubmissionResult} carries only anonymous anchors plus the proof
 * phrase shown once to the voter.
 */
public final class LinkedOfficesSubmissionService {

    private final PollDao pollDao;
    private final ElectionDefinitionService electionDefinitionService;
    private final LinkedElectionBallotValidator ballotValidator;
    private final LinkedBallotStorageService linkedBallotStorageService;
    private final BallotProofPhraseGenerator proofPhraseGenerator;

    public LinkedOfficesSubmissionService(DatabaseManager databaseManager) {
        Objects.requireNonNull(databaseManager, "databaseManager");
        this.pollDao = new PollDao(databaseManager);
        this.electionDefinitionService = new ElectionDefinitionService();
        this.ballotValidator = new LinkedElectionBallotValidator();
        this.linkedBallotStorageService = new LinkedBallotStorageService(databaseManager);
        this.proofPhraseGenerator = new BallotProofPhraseGenerator();
    }

    /**
     * Validates and submits a linked-offices ballot for a voter.
     *
     * <p>The full gate, in order: the poll must exist, be LINKED_OFFICES, and be
     * OPEN; its stored definition must validate; the ballot must validate against
     * that definition; then a fresh proof phrase is generated and the ballot is
     * stored transactionally. A failure at any step before storage writes
     * nothing, and the storage layer rolls back on any failure (including the
     * duplicate-participation check) so a rejected submission leaves no anonymous
     * ballot or contest-response rows behind.
     *
     * @return an identity-free summary including the one-time proof phrase
     * @throws PollServiceException if any gate fails or the participant already voted
     */
    public LinkedSubmissionResult submitLinkedOfficesBallot(long pollId,
                                                            String identityKey,
                                                            String clientPlatform,
                                                            LinkedElectionBallot ballot,
                                                            String ipHash,
                                                            String floodgateId) throws PollServiceException {
        requireNonBlank(identityKey, "identityKey");
        requireNonBlank(clientPlatform, "clientPlatform");
        Objects.requireNonNull(ballot, "ballot");

        try {
            Poll poll = pollDao.findPollById(pollId);
            if (poll == null) {
                throw new PollServiceException("Poll #" + pollId + " does not exist.");
            }
            if (poll.pollType() != PollType.LINKED_OFFICES) {
                throw new PollServiceException("Poll #" + pollId + " is not a LINKED_OFFICES poll.");
            }
            if (poll.status() != PollStatus.OPEN) {
                throw new PollServiceException("Poll #" + pollId + " is not open for voting.");
            }

            ElectionDefinitionService.ElectionDefinitionValidationResult definitionValidation =
                    electionDefinitionService.validate(poll.configJson());
            if (!definitionValidation.valid() || definitionValidation.definition().isEmpty()) {
                String detail = definitionValidation.issues().isEmpty()
                        ? "definition is not valid."
                        : String.join("; ", definitionValidation.issues());
                throw new PollServiceException("Poll #" + pollId
                        + " does not have a valid linked-offices definition: " + detail);
            }
            ElectionDefinition definition = definitionValidation.definition().get();

            // Validate the ballot against the definition before any write. The
            // storage layer re-validates through the canonicalizer, but doing it
            // here yields a clear, structured rejection message for the voter.
            BallotValidationResult validation = ballotValidator.validate(ballot);
            if (!validation.valid()) {
                throw new PollServiceException("Your ballot could not be accepted: "
                        + validation.issues().stream()
                        .map(BallotValidationIssue::message)
                        .collect(Collectors.joining(" ")));
            }

            String proofPhrase = proofPhraseGenerator.generate();
            Instant submittedAt = Instant.now();

            LinkedBallotStorageService.LinkedBallotStorageResult stored =
                    linkedBallotStorageService.storeLinkedOfficesBallot(
                            poll,
                            definition,
                            ballot,
                            identityKey,
                            clientPlatform,
                            ipHash,
                            floodgateId,
                            proofPhrase,
                            submittedAt);

            return new LinkedSubmissionResult(
                    pollId,
                    proofPhrase,
                    stored.ballotHash(),
                    stored.participationReceipt(),
                    stored.contestResponseRowCount(),
                    stored.submittedAt());
        } catch (PollServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new PollServiceException("Failed to submit linked-offices ballot for poll #" + pollId, e);
        }
    }

    private void requireNonBlank(String value, String fieldName) throws PollServiceException {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new PollServiceException(fieldName + " must not be blank.");
        }
    }

    /**
     * Identity-free summary of a successful linked-offices submission. The proof
     * phrase is the only sensitive field — it is shown to the voter once and is
     * the bearer token that can later reveal this anonymous ballot's content.
     */
    public record LinkedSubmissionResult(
            long pollId,
            String proofPhrase,
            String ballotHash,
            String participationReceipt,
            int contestResponseRowCount,
            Instant submittedAt
    ) {
        public LinkedSubmissionResult {
            Objects.requireNonNull(proofPhrase, "proofPhrase");
            Objects.requireNonNull(ballotHash, "ballotHash");
            Objects.requireNonNull(participationReceipt, "participationReceipt");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }
    }
}
