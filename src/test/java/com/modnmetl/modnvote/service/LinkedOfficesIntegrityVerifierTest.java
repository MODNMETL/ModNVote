package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionSerializer;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import com.modnmetl.modnvote.service.IntegrityVerificationService.IntegrityVerificationResult;
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
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.GRACE;
import static com.modnmetl.modnvote.service.LinkedStorageTestFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for linked-offices integrity verification
 * ({@link LinkedOfficesIntegrityVerifier}, the Bukkit-free collaborator
 * {@link IntegrityVerificationService} delegates the LINKED_OFFICES case to).
 *
 * <p>Each test stores a real linked-offices ballot through
 * {@link LinkedBallotStorageService}, optionally tampers with the stored anonymous
 * content directly in the database, and asserts the verifier's recount outcome.
 *
 * <p>The full {@link IntegrityVerificationService} cannot be constructed in a unit
 * test because its {@code PlatformAdapter} dependency exposes Bukkit types
 * ({@code Player}/{@code Plugin}) and the Paper API is {@code compileOnly} (absent
 * from the test classpath). The linked-offices logic therefore lives in this
 * standalone verifier — the same pattern as {@link LinkedBallotStorageService} —
 * so it is fully testable. The unchanged single-contest (YES_NO /
 * RANKED_SINGLE_WINNER) recompute formula is regression-locked separately in
 * {@code BallotCanonicalizerTest} and {@code BallotHashingServiceTest}.
 */
class LinkedOfficesIntegrityVerifierTest {

    private static final Instant T = Instant.ofEpochMilli(1000L);
    private static final String PROOF = "river-stone-maple-fox";

    @Test
    void validLinkedOfficeBallotPassesIntegrity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "valid");
        storeBallot(ctx, "id-1", PROOF, T);

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertTrue(result.ballotHashesValid(), () -> "expected valid, issues: " + result.issues());
        assertTrue(result.recordCountsMatch());
        assertTrue(result.auditChainValid());
        assertTrue(result.overallValid());
        assertTrue(result.issues().isEmpty(), () -> "unexpected issues: " + result.issues());
    }

    @Test
    void tamperedContestResponseFailsIntegrity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "tamper-row");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        // Swap a stored council selection (dave -> erin); erin is also council-eligible,
        // so reconstruction stays valid but the canonical payload — and hash — change.
        updateCandidateKey(ctx.dbm, ballotId, COUNCIL, DAVE, ERIN);

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertFalse(result.ballotHashesValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("ballot hash verification")),
                () -> "issues: " + result.issues());
    }

    @Test
    void tamperedAnonymousBallotHashFailsIntegrity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "tamper-hash");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        exec(ctx.dbm, "UPDATE anonymous_ballots SET ballot_hash = 'deadbeef' WHERE anonymous_ballot_id = " + ballotId);

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertFalse(result.ballotHashesValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("ballot hash verification")
                        && i.contains("actual=deadbeef")),
                () -> "issues: " + result.issues());
    }

    @Test
    void missingContestRowsFailsIntegrity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "missing-rows");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        exec(ctx.dbm, "DELETE FROM anonymous_ballot_contest_responses WHERE anonymous_ballot_id = " + ballotId);

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertFalse(result.ballotHashesValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("no contest-response rows")),
                () -> "issues: " + result.issues());
    }

    @Test
    void invalidConfigJsonFailsIntegrity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "bad-config");
        storeBallot(ctx, "id-1", PROOF, T);

        // Same poll id and stored ballots, but the definition in config_json is absent.
        Poll pollWithBadConfig = linkedPoll(ctx.poll.pollId(), ctx.poll.slug(), "{}");

        IntegrityVerificationResult result = ctx.verifier.verify(pollWithBadConfig);

        assertFalse(result.ballotHashesValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("invalid or missing linked-offices definition")),
                () -> "issues: " + result.issues());
    }

    @Test
    void unknownCandidateRowFailsIntegrity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "unknown-candidate");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        // Inject a malformed ranked mayor row for a candidate that does not exist.
        exec(ctx.dbm, "INSERT INTO anonymous_ballot_contest_responses "
                + "(anonymous_ballot_id, office_key, response_type, candidate_key, rank_position, selection_order) "
                + "VALUES (" + ballotId + ", '" + MAYOR + "', 'RANKED', 'ghost-candidate', 4, NULL)");

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertFalse(result.ballotHashesValid());
        assertTrue(result.issues().stream().anyMatch(i ->
                        i.contains("validation during recount") || i.contains("could not be reconstructed")),
                () -> "issues: " + result.issues());
    }

    @Test
    void mixedResponseTypesInOfficeAreReportedAsReconstructionFailure(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "mixed-types");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        // Mayor is RANKED; inject an APPROVAL row into the same office.
        exec(ctx.dbm, "INSERT INTO anonymous_ballot_contest_responses "
                + "(anonymous_ballot_id, office_key, response_type, candidate_key, rank_position, selection_order) "
                + "VALUES (" + ballotId + ", '" + MAYOR + "', 'APPROVAL', '" + ALICE + "-x', NULL, 1)");

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertFalse(result.ballotHashesValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("could not be reconstructed")),
                () -> "issues: " + result.issues());
    }

    @Test
    void failureReportsContainNoVoterIdentity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "privacy");
        String identityKey = "player-uuid-1234-secret";
        String ipHash = "ip-hash-abcdef";
        String floodgateId = "floodgate-id-9999";
        long ballotId = storeBallot(ctx, identityKey, PROOF, T, "JAVA", ipHash, floodgateId);

        // Force a failure so issue strings are populated.
        exec(ctx.dbm, "UPDATE anonymous_ballots SET ballot_hash = 'deadbeef' WHERE anonymous_ballot_id = " + ballotId);

        IntegrityVerificationResult result = ctx.verifier.verify(ctx.poll);

        assertFalse(result.issues().isEmpty());
        String joined = String.join("\n", result.issues());
        assertFalse(joined.contains(identityKey), "issues leaked identity key");
        assertFalse(joined.contains(ipHash), "issues leaked ip hash");
        assertFalse(joined.contains(floodgateId), "issues leaked floodgate id");
        assertFalse(joined.toLowerCase().contains(PROOF), "issues leaked proof phrase");
    }

    // --- helpers --------------------------------------------------------------

    private record Ctx(DatabaseManager dbm, LinkedBallotStorageService service,
                       LinkedOfficesIntegrityVerifier verifier, Poll poll, ElectionDefinition definition) {
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
                new LinkedOfficesIntegrityVerifier(dbm), poll, def);
    }

    private static long storeBallot(Ctx ctx, String identityKey, String proof, Instant submittedAt) throws Exception {
        return storeBallot(ctx, identityKey, proof, submittedAt, "JAVA", null, null);
    }

    private static long storeBallot(Ctx ctx, String identityKey, String proof, Instant submittedAt,
                                    String platform, String ipHash, String floodgateId) throws Exception {
        LinkedElectionBallot ballot = new LinkedElectionBallot(ctx.definition, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(GRACE, ALICE, DAVE))));
        return ctx.service.storeLinkedOfficesBallot(
                ctx.poll, ctx.definition, ballot, identityKey, platform, ipHash, floodgateId, proof, submittedAt)
                .anonymousBallotId();
    }

    private static Poll linkedPoll(long pollId, String slug, String configJson) {
        return new Poll(pollId, slug, "Linked Offices Poll", "Description",
                PollType.LINKED_OFFICES, PollStatus.DRAFT, null, null,
                1, 3, false, true, "participation-secret-" + pollId, configJson);
    }

    private static void updateCandidateKey(DatabaseManager dbm, long ballotId, String office,
                                           String from, String to) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE anonymous_ballot_contest_responses SET candidate_key = ? "
                             + "WHERE anonymous_ballot_id = ? AND office_key = ? AND candidate_key = ?")) {
            ps.setString(1, to);
            ps.setLong(2, ballotId);
            ps.setString(3, office);
            ps.setString(4, from);
            ps.executeUpdate();
        }
    }

    private static void exec(DatabaseManager dbm, String sql) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
