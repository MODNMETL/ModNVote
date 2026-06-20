package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.ALICE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.BOB;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.CAROL;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.COUNCIL;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.DAVE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.GRACE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hardening tests for {@link LinkedBallotReconstructor}: it must rebuild a ballot
 * deterministically from well-formed rows and refuse (not silently repair)
 * malformed stored rows, since the rows are recount/integrity input.
 */
class LinkedBallotReconstructorTest {

    private final LinkedBallotReconstructor reconstructor = new LinkedBallotReconstructor();
    private final ElectionDefinition definition = LinkedStorageTestFixtures.mayorCouncil();

    private static AnonymousBallotContestResponse ranked(String office, String candidate, int rank) {
        return new AnonymousBallotContestResponse(0, 1, office, "RANKED", candidate, rank, null, "t");
    }

    private static AnonymousBallotContestResponse approval(String office, String candidate, int order) {
        return new AnonymousBallotContestResponse(0, 1, office, "APPROVAL", candidate, null, order, "t");
    }

    @Test
    void reconstructsRankedByRankAndApprovalBySelectionOrder() {
        // Rows deliberately out of physical order; ordering comes from the positions.
        List<AnonymousBallotContestResponse> rows = List.of(
                ranked(MAYOR, CAROL, 3),
                ranked(MAYOR, ALICE, 1),
                ranked(MAYOR, BOB, 2),
                approval(COUNCIL, GRACE, 3),
                approval(COUNCIL, ALICE, 1),
                approval(COUNCIL, DAVE, 2));

        LinkedElectionBallot ballot = reconstructor.reconstruct(definition, rows);

        RankedContestVote mayor = (RankedContestVote) ballot.findResponse(MAYOR).orElseThrow();
        assertEquals(List.of(ALICE, BOB, CAROL), mayor.orderedCandidateKeys());

        ApprovalContestVote council = (ApprovalContestVote) ballot.findResponse(COUNCIL).orElseThrow();
        assertEquals(List.of(ALICE, DAVE, GRACE), council.selectedCandidateKeys());
    }

    @Test
    void mixedResponseTypesInOneOfficeThrows() {
        List<AnonymousBallotContestResponse> rows = List.of(
                ranked(MAYOR, ALICE, 1),
                approval(MAYOR, BOB, 1));

        assertThrows(LinkedBallotReconstructionException.class,
                () -> reconstructor.reconstruct(definition, rows));
    }

    @Test
    void duplicateCandidateRowsThrow() {
        List<AnonymousBallotContestResponse> rows = List.of(
                ranked(MAYOR, ALICE, 1),
                ranked(MAYOR, ALICE, 2));

        assertThrows(LinkedBallotReconstructionException.class,
                () -> reconstructor.reconstruct(definition, rows));
    }

    @Test
    void missingRankPositionThrows() {
        List<AnonymousBallotContestResponse> rows = List.of(
                ranked(MAYOR, ALICE, 1),
                new AnonymousBallotContestResponse(0, 1, MAYOR, "RANKED", BOB, null, null, "t"));

        assertThrows(LinkedBallotReconstructionException.class,
                () -> reconstructor.reconstruct(definition, rows));
    }

    @Test
    void duplicateSelectionOrderThrows() {
        List<AnonymousBallotContestResponse> rows = List.of(
                approval(COUNCIL, ALICE, 1),
                approval(COUNCIL, DAVE, 1));

        assertThrows(LinkedBallotReconstructionException.class,
                () -> reconstructor.reconstruct(definition, rows));
    }

    @Test
    void unknownResponseTypeThrows() {
        List<AnonymousBallotContestResponse> rows = List.of(
                new AnonymousBallotContestResponse(0, 1, MAYOR, "WEIRD", ALICE, 1, null, "t"));

        assertThrows(LinkedBallotReconstructionException.class,
                () -> reconstructor.reconstruct(definition, rows));
    }
}
