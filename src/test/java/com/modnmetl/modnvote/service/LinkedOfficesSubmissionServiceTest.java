package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionSerializer;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import com.modnmetl.modnvote.domain.election.results.ContestResult;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.ALICE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.BOB;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.CAROL;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.COUNCIL;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.DAVE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.ERIN;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.FRANK;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.GRACE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the player-facing linked-offices submission path
 * ({@link LinkedOfficesSubmissionService}) against a temporary database. Covers
 * the OPEN lifecycle gate, single-of-each write guarantee, duplicate prevention
 * with rollback, invalid-ballot rejection, and result integration after close.
 */
class LinkedOfficesSubmissionServiceTest {

    private record Ctx(DatabaseManager dbm, LinkedOfficesSubmissionService service,
                       ElectionDefinition definition, String configJson) {
    }

    private static Ctx setup(Path tempDir, String name) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve(name + ".db"));
        new SchemaInitializer(dbm).initialize();
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();
        String configJson = new ElectionDefinitionSerializer().serialize(def);
        return new Ctx(dbm, new LinkedOfficesSubmissionService(dbm), def, configJson);
    }

    private static long createPoll(Ctx ctx, PollStatus status, String name) throws Exception {
        PollDao pollDao = new PollDao(ctx.dbm);
        Poll draft = new Poll(0L, "linked-" + name, "Linked Offices Poll", "Description",
                PollType.LINKED_OFFICES, PollStatus.DRAFT, null, null,
                1, 3, false, true, "secret-" + name, ctx.configJson);
        try (Connection connection = ctx.dbm.getConnection()) {
            long pollId = pollDao.insertPoll(connection, draft, "tester", "DEFAULT", ctx.configJson);
            if (status != PollStatus.DRAFT) {
                pollDao.updatePollStatus(connection, pollId, status);
            }
            return pollId;
        }
    }

    private static LinkedElectionBallot ballot(ElectionDefinition def,
                                               List<String> mayorRanking,
                                               List<String> councilApprovals) {
        return new LinkedElectionBallot(def, List.<ContestVote>of(
                new RankedContestVote(MAYOR, mayorRanking),
                new ApprovalContestVote(COUNCIL, councilApprovals)));
    }

    private static int count(DatabaseManager dbm, String sql) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void successStoresExactlyOneOfEachAndProofVerifies(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "success");
        long pollId = createPoll(ctx, PollStatus.OPEN, "success");

        LinkedOfficesSubmissionService.LinkedSubmissionResult result =
                ctx.service.submitLinkedOfficesBallot(pollId, "voter-uuid-1", "JAVA_GUI_LINKED_OFFICES",
                        ballot(ctx.definition, List.of(ALICE, BOB), List.of(DAVE, ERIN, GRACE)), "ip-hash", null);

        assertEquals(pollId, result.pollId());
        assertEquals(5, result.contestResponseRowCount(), "2 mayor ranks + 3 council approvals");
        assertEquals(1, count(ctx.dbm, "SELECT COUNT(*) FROM participation_records WHERE poll_id=" + pollId));
        assertEquals(1, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballots WHERE poll_id=" + pollId));
        assertEquals(5, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballot_contest_responses r "
                + "JOIN anonymous_ballots b ON b.anonymous_ballot_id=r.anonymous_ballot_id WHERE b.poll_id=" + pollId));

        // The proof phrase issued at submission verifies the stored anonymous ballot.
        Poll closed = closeAndLoad(ctx, pollId);
        LinkedOfficeBallotProofVerificationResult proof =
                new LinkedOfficesProofVerifier(ctx.dbm).verify(closed, result.proofPhrase());
        assertTrue(proof.ballotFound());
        assertTrue(proof.verified(), () -> "proof failure: " + proof.failureReason());
        assertEquals(result.ballotHash(), proof.ballotHash());
    }

    @Test
    void openGateRejectsNonOpenAndAcceptsOpen(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "gate");

        for (PollStatus blocked : List.of(PollStatus.DRAFT, PollStatus.READY, PollStatus.CLOSED)) {
            long pollId = createPoll(ctx, blocked, "gate-" + blocked.name());
            PollServiceException ex = assertThrows(PollServiceException.class, () ->
                    ctx.service.submitLinkedOfficesBallot(pollId, "voter", "JAVA",
                            ballot(ctx.definition, List.of(ALICE), List.of(DAVE)), null, null));
            assertTrue(ex.getMessage().contains("not open"), () -> "message: " + ex.getMessage());
            assertEquals(0, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballots WHERE poll_id=" + pollId));
        }

        long openPoll = createPoll(ctx, PollStatus.OPEN, "gate-open");
        ctx.service.submitLinkedOfficesBallot(openPoll, "voter", "JAVA",
                ballot(ctx.definition, List.of(ALICE), List.of(DAVE)), null, null);
        assertEquals(1, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballots WHERE poll_id=" + openPoll));
    }

    @Test
    void duplicateSubmissionIsRejectedAndDoesNotWriteSecondBallot(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "dupe");
        long pollId = createPoll(ctx, PollStatus.OPEN, "dupe");

        ctx.service.submitLinkedOfficesBallot(pollId, "same-voter", "JAVA",
                ballot(ctx.definition, List.of(ALICE, BOB), List.of(DAVE, ERIN)), null, null);

        assertThrows(PollServiceException.class, () ->
                ctx.service.submitLinkedOfficesBallot(pollId, "same-voter", "JAVA",
                        ballot(ctx.definition, List.of(BOB, CAROL), List.of(FRANK, GRACE)), null, null));

        assertEquals(1, count(ctx.dbm, "SELECT COUNT(*) FROM participation_records WHERE poll_id=" + pollId));
        assertEquals(1, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballots WHERE poll_id=" + pollId));
        assertEquals(4, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballot_contest_responses r "
                + "JOIN anonymous_ballots b ON b.anonymous_ballot_id=r.anonymous_ballot_id WHERE b.poll_id=" + pollId));
    }

    @Test
    void invalidBallotIsRejectedAndWritesNothing(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "invalid");
        long pollId = createPoll(ctx, PollStatus.OPEN, "invalid");

        // Too many approvals (maxSelections = 3) and an unknown candidate.
        assertThrows(PollServiceException.class, () ->
                ctx.service.submitLinkedOfficesBallot(pollId, "voter-a", "JAVA",
                        ballot(ctx.definition, List.of(ALICE), List.of(DAVE, ERIN, FRANK, GRACE)), null, null));
        assertThrows(PollServiceException.class, () ->
                ctx.service.submitLinkedOfficesBallot(pollId, "voter-b", "JAVA",
                        ballot(ctx.definition, List.of("ghost"), List.of(DAVE)), null, null));
        // Wrong response type: an approval response for the IRV Mayor office.
        assertThrows(PollServiceException.class, () ->
                ctx.service.submitLinkedOfficesBallot(pollId, "voter-c", "JAVA",
                        new LinkedElectionBallot(ctx.definition, List.<ContestVote>of(
                                new ApprovalContestVote(MAYOR, List.of(ALICE)))), null, null));

        assertEquals(0, count(ctx.dbm, "SELECT COUNT(*) FROM participation_records WHERE poll_id=" + pollId));
        assertEquals(0, count(ctx.dbm, "SELECT COUNT(*) FROM anonymous_ballots WHERE poll_id=" + pollId));
    }

    @Test
    void submittedBallotsCountToExpectedWinnersAfterClose(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "result");
        long pollId = createPoll(ctx, PollStatus.OPEN, "result");

        submit(ctx, pollId, "v1", List.of(ALICE, BOB), List.of(ALICE, DAVE, GRACE));
        submit(ctx, pollId, "v2", List.of(ALICE, CAROL), List.of(DAVE, ERIN, GRACE));
        submit(ctx, pollId, "v3", List.of(ALICE), List.of(DAVE, GRACE, FRANK));
        submit(ctx, pollId, "v4", List.of(BOB, ALICE), List.of(ERIN, GRACE, DAVE));
        submit(ctx, pollId, "v5", List.of(CAROL), List.of(DAVE, GRACE, ERIN));

        Poll closed = closeAndLoad(ctx, pollId);
        LinkedElectionResult result = new LinkedElectionResultService(ctx.dbm).computeResult(closed);

        assertEquals(5, result.countedBallots());
        assertTrue(result.complete(), () -> "issues: " + result.issues());

        ContestResult mayor = result.findContest(MAYOR).orElseThrow();
        assertEquals(List.of(ALICE), mayor.winners());

        ContestResult council = result.findContest(COUNCIL).orElseThrow();
        assertTrue(council.excludedCandidateKeys().contains(ALICE));
        assertFalse(council.winners().contains(ALICE));
        assertEquals(List.of(DAVE, GRACE, ERIN), council.winners());
    }

    private static void submit(Ctx ctx, long pollId, String identity,
                               List<String> mayorRanking, List<String> councilApprovals) throws Exception {
        ctx.service.submitLinkedOfficesBallot(pollId, identity, "JAVA_GUI_LINKED_OFFICES",
                ballot(ctx.definition, mayorRanking, councilApprovals), null, null);
    }

    private static Poll closeAndLoad(Ctx ctx, long pollId) throws Exception {
        PollDao pollDao = new PollDao(ctx.dbm);
        try (Connection connection = ctx.dbm.getConnection()) {
            pollDao.updatePollStatus(connection, pollId, PollStatus.CLOSED);
        }
        return pollDao.findPollById(pollId);
    }
}
