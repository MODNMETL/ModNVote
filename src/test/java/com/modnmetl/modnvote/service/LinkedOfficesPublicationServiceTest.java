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
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import com.modnmetl.modnvote.presentation.LinkedElectionWitnessPayloadFormatter;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.logging.Logger;

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
 * Service-level tests for the linked-offices close / publishresult publication
 * path. The witness publication service itself requires a running Bukkit
 * plugin, so these tests exercise the exact collaborators the thin
 * {@code PollCommand} branch wires together: closing a linked poll, computing
 * the linked result, the CLOSED gate enforced for publishresult, and the
 * deterministic witness payload the publication overload renders from that
 * result.
 */
class LinkedOfficesPublicationServiceTest {

    private record Ctx(DatabaseManager dbm, LinkedOfficesSubmissionService submission,
                       PollService pollService, ResultService resultService,
                       ElectionDefinition definition, String configJson) {
    }

    private static Ctx setup(Path tempDir, String name) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve(name + ".db"));
        new SchemaInitializer(dbm).initialize();
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();
        String configJson = new ElectionDefinitionSerializer().serialize(def);
        Logger logger = Logger.getLogger("LinkedOfficesPublicationServiceTest");
        return new Ctx(dbm, new LinkedOfficesSubmissionService(dbm),
                new PollService(dbm, logger), new ResultService(dbm, logger), def, configJson);
    }

    private static long createPoll(Ctx ctx, PollStatus status, String name) throws Exception {
        PollDao pollDao = new PollDao(ctx.dbm);
        Poll draft = new Poll(0L, "linked-" + name, "Town Election", "Description",
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

    private static void submit(Ctx ctx, long pollId, String identity,
                               List<String> mayorRanking, List<String> councilApprovals) throws Exception {
        LinkedElectionBallot ballot = new LinkedElectionBallot(ctx.definition, List.<ContestVote>of(
                new RankedContestVote(MAYOR, mayorRanking),
                new ApprovalContestVote(COUNCIL, councilApprovals)));
        ctx.submission.submitLinkedOfficesBallot(pollId, identity, "JAVA_GUI_LINKED_OFFICES",
                ballot, null, null);
    }

    @Test
    void closeThenComputeAndPublishLinkedResult(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "close");
        long pollId = createPoll(ctx, PollStatus.OPEN, "close");

        submit(ctx, pollId, "v1", List.of(ALICE, BOB), List.of(ALICE, DAVE, GRACE));
        submit(ctx, pollId, "v2", List.of(ALICE, CAROL), List.of(DAVE, ERIN, GRACE));
        submit(ctx, pollId, "v3", List.of(ALICE), List.of(DAVE, GRACE, FRANK));
        submit(ctx, pollId, "v4", List.of(BOB, ALICE), List.of(ERIN, GRACE, DAVE));
        submit(ctx, pollId, "v5", List.of(CAROL), List.of(DAVE, GRACE, ERIN));

        // Close is type-agnostic: status flips to CLOSED without the single-contest path.
        ctx.pollService.closePoll(pollId, "admin");
        Poll closed = new PollDao(ctx.dbm).findPollById(pollId);
        assertEquals(PollStatus.CLOSED, closed.status());

        // The close/publishresult path computes the linked result from anonymous content.
        LinkedElectionResult result = ctx.resultService.getLinkedElectionResult(pollId);
        assertEquals(5, result.countedBallots());
        assertTrue(result.complete(), () -> "issues: " + result.issues());
        assertEquals(List.of(ALICE), result.findContest(MAYOR).orElseThrow().winners());

        // The publication overload renders that linked result into witness fields.
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> fields =
                LinkedElectionWitnessPayloadFormatter.buildFields(closed, result, closed.closesAt(), 1024);
        String all = fields.stream()
                .map(f -> f.name() + "=" + f.value())
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all.contains("Type=LINKED_OFFICES"), all);
        assertTrue(all.contains("Counted Ballots=5"), all);
        assertTrue(all.contains("Winners: " + ALICE), all);
        assertTrue(all.contains("Excluded by dependency: " + ALICE), all);
    }

    @Test
    void publishResultRejectsNonClosedLinkedPoll(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "gate");
        long openPoll = createPoll(ctx, PollStatus.OPEN, "gate");

        // publishresult requires CLOSED; the computation path enforces it too.
        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> ctx.resultService.getLinkedElectionResult(openPoll));
        assertTrue(ex.getMessage().contains("still open"), () -> "message: " + ex.getMessage());
    }

    @Test
    void witnessPayloadNeverLeaksIdentity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "privacy");
        long pollId = createPoll(ctx, PollStatus.OPEN, "privacy");
        submit(ctx, pollId, "secret-voter-uuid", List.of(ALICE, BOB), List.of(DAVE, ERIN, GRACE));

        ctx.pollService.closePoll(pollId, "admin");
        Poll closed = new PollDao(ctx.dbm).findPollById(pollId);
        LinkedElectionResult result = ctx.resultService.getLinkedElectionResult(pollId);

        List<LinkedElectionWitnessPayloadFormatter.WitnessField> fields =
                LinkedElectionWitnessPayloadFormatter.buildFields(closed, result, closed.closesAt(), 1024);
        String all = fields.stream()
                .map(f -> f.name() + "=" + f.value())
                .reduce("", (a, b) -> a + "\n" + b)
                .toLowerCase();
        for (String forbidden : List.of("secret-voter-uuid", "uuid", "floodgate",
                "participation", "receipt", "proof", "token")) {
            assertFalse(all.contains(forbidden), () -> "payload leaked '" + forbidden + "': " + all);
        }
    }
}
