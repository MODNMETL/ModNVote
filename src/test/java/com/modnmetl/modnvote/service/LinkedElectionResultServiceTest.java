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
import java.time.Instant;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for linked-offices result calculation over stored anonymous
 * ballots ({@link LinkedElectionResultService}).
 *
 * <p>Each test stores real anonymous ballots through {@link LinkedBallotStorageService}
 * (writing only anonymous content rows) and then reconstructs and counts them. The
 * full {@code ResultService} cannot be unit-constructed cheaply here, but the
 * LINKED_OFFICES path it delegates to is this standalone, Bukkit-free collaborator —
 * the same pattern as {@link LinkedOfficesIntegrityVerifier}. The unchanged
 * single-contest {@code ResultService} path is regression-locked separately in
 * {@code ResultServiceSingleContestRegressionTest}.
 */
class LinkedElectionResultServiceTest {

    private static final Instant T = Instant.ofEpochMilli(1000L);
    private static final String PROOF = "river-stone-maple-fox";

    @Test
    void countsMayorIrvWinnerAndCouncilTopNExcludingMayorWinner(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "count");
        storeStandardElectorate(ctx);

        LinkedElectionResult result = ctx.service.computeResult(ctx.poll);

        assertEquals(5, result.countedBallots());
        assertEquals(0, result.skippedBallots());
        assertTrue(result.complete(), () -> "issues: " + result.issues());

        ContestResult mayor = result.findContest(MAYOR).orElseThrow();
        assertEquals(List.of(ALICE), mayor.winners());

        ContestResult council = result.findContest(COUNCIL).orElseThrow();
        // Alice won mayor, so she is excluded from council; remaining approvals:
        // dave=5, grace=5, erin=3, frank=1 -> top 3 by score then contest order.
        assertTrue(council.excludedCandidateKeys().contains(ALICE));
        assertFalse(council.winners().contains(ALICE));
        assertEquals(List.of(DAVE, GRACE, ERIN), council.winners());
    }

    @Test
    void corruptStoredBallotIsSkippedAndCountingContinues(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "corrupt");
        storeStandardElectorate(ctx);

        // Corrupt one stored ballot by injecting a mixed response type into the mayor
        // office, which makes reconstruction fail for that ballot only.
        long ballotId = firstAnonymousBallotId(ctx.dbm);
        exec(ctx.dbm, "INSERT INTO anonymous_ballot_contest_responses "
                + "(anonymous_ballot_id, office_key, response_type, candidate_key, rank_position, selection_order) "
                + "VALUES (" + ballotId + ", '" + MAYOR + "', 'APPROVAL', '" + CAROL + "', NULL, 1)");

        LinkedElectionResult result = ctx.service.computeResult(ctx.poll);

        assertEquals(1, result.skippedBallots());
        assertEquals(4, result.countedBallots());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("could not be reconstructed")),
                () -> "issues: " + result.issues());
        // Counting still produced both contests and did not crash.
        assertTrue(result.findContest(MAYOR).isPresent());
        assertTrue(result.findContest(COUNCIL).isPresent());
    }

    @Test
    void resultOutputContainsNoVoterIdentity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "privacy");
        String identityKey = "player-uuid-1234-secret";
        String ipHash = "ip-hash-abcdef";
        String floodgateId = "floodgate-id-9999";

        LinkedElectionBallot ballot = new LinkedElectionBallot(ctx.definition, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, GRACE, ERIN))));
        ctx.service0.storeLinkedOfficesBallot(
                ctx.poll, ctx.definition, ballot, identityKey, "JAVA", ipHash, floodgateId, PROOF, T);

        LinkedElectionResult result = ctx.service.computeResult(ctx.poll);

        StringBuilder rendered = new StringBuilder();
        rendered.append(String.join("\n", result.issues()));
        for (ContestResult contest : result.contestResults()) {
            rendered.append('\n').append(contest.officeKey()).append('\n')
                    .append(String.join(",", contest.winners()));
        }
        String joined = rendered.toString();
        assertFalse(joined.contains(identityKey), "result leaked identity key");
        assertFalse(joined.contains(ipHash), "result leaked ip hash");
        assertFalse(joined.contains(floodgateId), "result leaked floodgate id");
        assertFalse(joined.toLowerCase().contains(PROOF), "result leaked proof phrase");
    }

    // --- helpers --------------------------------------------------------------

    private record Ctx(DatabaseManager dbm, LinkedBallotStorageService service0,
                       LinkedElectionResultService service, Poll poll, ElectionDefinition definition) {
    }

    private static Ctx setup(Path tempDir, String name) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve(name + ".db"));
        new SchemaInitializer(dbm).initialize();

        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();
        String configJson = new ElectionDefinitionSerializer().serialize(def);

        PollDao pollDao = new PollDao(dbm);
        long pollId;
        try (Connection connection = dbm.getConnection()) {
            pollId = pollDao.insertPoll(
                    connection, linkedPoll(0, "linked-" + name, configJson), "tester", "DEFAULT", configJson);
        }
        Poll poll = linkedPoll(pollId, "linked-" + name, configJson);
        return new Ctx(dbm, new LinkedBallotStorageService(dbm),
                new LinkedElectionResultService(dbm), poll, def);
    }

    private static void storeStandardElectorate(Ctx ctx) throws Exception {
        store(ctx, "v1", List.of(ALICE, BOB), List.of(ALICE, DAVE, GRACE));
        store(ctx, "v2", List.of(ALICE, CAROL), List.of(DAVE, ERIN, GRACE));
        store(ctx, "v3", List.of(ALICE), List.of(DAVE, GRACE, FRANK));
        store(ctx, "v4", List.of(BOB, ALICE), List.of(ERIN, GRACE, DAVE));
        store(ctx, "v5", List.of(CAROL), List.of(DAVE, GRACE, ERIN));
    }

    private static void store(Ctx ctx, String identityKey,
                              List<String> mayorRanking, List<String> councilApprovals) throws Exception {
        LinkedElectionBallot ballot = new LinkedElectionBallot(ctx.definition, List.<ContestVote>of(
                new RankedContestVote(MAYOR, mayorRanking),
                new ApprovalContestVote(COUNCIL, councilApprovals)));
        ctx.service0.storeLinkedOfficesBallot(
                ctx.poll, ctx.definition, ballot, identityKey, "JAVA", null, null, PROOF + "-" + identityKey, T);
    }

    private static Poll linkedPoll(long pollId, String slug, String configJson) {
        return new Poll(pollId, slug, "Linked Offices Poll", "Description",
                PollType.LINKED_OFFICES, PollStatus.CLOSED, null, null,
                1, 3, false, true, "participation-secret-" + pollId, configJson);
    }

    private static long firstAnonymousBallotId(DatabaseManager dbm) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT MIN(anonymous_ballot_id) FROM anonymous_ballots");
             var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void exec(DatabaseManager dbm, String sql) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
