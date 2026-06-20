package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.results.ContestResult;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionCountingService;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import com.modnmetl.modnvote.storage.AnonymousBallotContestResponseDao;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads anonymous linked-offices ballot content and computes the election result.
 *
 * <p>This is a Bukkit-free collaborator holding only a {@link DatabaseManager}, the
 * same pattern as {@link LinkedOfficesIntegrityVerifier} and
 * {@link LinkedBallotStorageService}, so it is fully unit-testable. It is the
 * bridge between stored anonymous content and the pure
 * {@link LinkedElectionCountingService}.
 *
 * <p><strong>Privacy.</strong> Counting reads only {@code anonymous_ballots} and
 * {@code anonymous_ballot_contest_responses} plus the poll's election definition.
 * It never reads or joins {@code participation_records}, player UUID/name, IP hash,
 * Floodgate id, participation token, or receipt. The returned result carries
 * anonymous office/candidate keys and counts only.
 *
 * <p><strong>Robustness.</strong> A stored ballot whose rows cannot be
 * reconstructed (malformed/inconsistent content) is skipped and recorded as an
 * identity-free issue rather than crashing the count. This is read-only: it never
 * mutates poll state, applies finality, or writes audit events.
 */
public final class LinkedElectionResultService {

    private final AnonymousBallotDao anonymousBallotDao;
    private final AnonymousBallotContestResponseDao anonymousBallotContestResponseDao;
    private final ElectionDefinitionService electionDefinitionService;
    private final LinkedBallotReconstructor linkedBallotReconstructor;
    private final LinkedElectionCountingService countingService;

    public LinkedElectionResultService(DatabaseManager databaseManager) {
        Objects.requireNonNull(databaseManager, "databaseManager");
        this.anonymousBallotDao = new AnonymousBallotDao(databaseManager);
        this.anonymousBallotContestResponseDao = new AnonymousBallotContestResponseDao(databaseManager);
        this.electionDefinitionService = new ElectionDefinitionService();
        this.linkedBallotReconstructor = new LinkedBallotReconstructor();
        this.countingService = new LinkedElectionCountingService();
    }

    /**
     * Reconstructs and counts every anonymous ballot for a linked-offices poll.
     *
     * @param poll the LINKED_OFFICES poll to compute a result for
     * @return the deterministic, anonymous election result
     * @throws PollServiceException if the poll has no valid linked-offices definition
     * @throws Exception            on a database error
     */
    public LinkedElectionResult computeResult(Poll poll) throws Exception {
        Objects.requireNonNull(poll, "poll");
        long pollId = poll.pollId();

        ElectionDefinitionService.ElectionDefinitionValidationResult definitionResult =
                electionDefinitionService.validate(poll);
        if (definitionResult.definition().isEmpty() || !definitionResult.valid()) {
            throw new PollServiceException("Poll #" + pollId
                    + " has an invalid or missing linked-offices definition: "
                    + String.join("; ", definitionResult.issues()));
        }
        ElectionDefinition definition = definitionResult.definition().get();

        List<AnonymousBallotDao.StoredAnonymousBallot> stored =
                anonymousBallotDao.findAnonymousBallotsByPollId(pollId);

        List<LinkedElectionBallot> ballots = new ArrayList<>();
        List<String> loadIssues = new ArrayList<>();
        int skipped = 0;

        for (AnonymousBallotDao.StoredAnonymousBallot ballot : stored) {
            long ballotId = ballot.anonymousBallotId();
            List<AnonymousBallotContestResponse> rows =
                    anonymousBallotContestResponseDao.findResponsesByAnonymousBallotId(ballotId);

            if (rows.isEmpty()) {
                skipped++;
                loadIssues.add("Anonymous ballot #" + ballotId
                        + " has no contest-response rows and was skipped.");
                continue;
            }

            try {
                ballots.add(linkedBallotReconstructor.reconstruct(definition, rows));
            } catch (LinkedBallotReconstructionException e) {
                skipped++;
                loadIssues.add("Anonymous ballot #" + ballotId
                        + " could not be reconstructed and was skipped: " + e.getMessage());
            }
        }

        LinkedElectionResult counted = countingService.count(poll, definition, ballots);

        // Fold the load-time skip count and issues into the counting result.
        List<String> mergedIssues = new ArrayList<>(loadIssues);
        mergedIssues.addAll(counted.issues());
        List<ContestResult> contestResults = counted.contestResults();

        return new LinkedElectionResult(
                pollId,
                poll.title(),
                counted.complete(),
                counted.countedBallots(),
                skipped,
                contestResults,
                mergedIssues);
    }
}
