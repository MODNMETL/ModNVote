package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import com.modnmetl.modnvote.service.canonical.BallotCanonicalizer;
import com.modnmetl.modnvote.storage.AnonymousBallotContestResponseDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HexFormat;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link LinkedBallotStorageService}: transactional storage
 * of linked-offices anonymous ballot content, hash wiring, rollback behaviour,
 * canonical row order, and reconstruction.
 */
class LinkedBallotStorageServiceTest {

    private static final Instant T = Instant.ofEpochMilli(1000L);
    private static final String PROOF = "river-stone-maple-fox";

    private final BallotCanonicalizer canonicalizer = new BallotCanonicalizer();

    @Test
    void storesOneParticipationOneBallotAndContestRowsWithCorrectHashes(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "store");
        Poll poll = ctx.poll;
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();

        LinkedElectionBallot ballot = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(GRACE, ALICE, DAVE)))); // non-canonical order

        LinkedBallotStorageService.LinkedBallotStorageResult result =
                ctx.service.storeLinkedOfficesBallot(poll, def, ballot, "id-1", "JAVA", null, null, PROOF, T);

        // Exactly one participation record + one anonymous ballot.
        assertEquals(1, countRows(ctx.dbm, "participation_records", poll.pollId()));
        assertEquals(1, countRows(ctx.dbm, "anonymous_ballots", poll.pollId()));

        // Contest response rows: 3 ranked mayor + 3 approval council.
        List<AnonymousBallotContestResponse> rows =
                new AnonymousBallotContestResponseDao(ctx.dbm)
                        .findResponsesByAnonymousBallotId(result.anonymousBallotId());
        assertEquals(6, rows.size());
        assertEquals(6, result.contestResponseRowCount());

        // ballot_hash is SHA-256 of the linked-offices canonical payload.
        String expectedPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(poll, def, ballot, T);
        assertEquals(expectedPayload, result.canonicalPayload());
        assertEquals(sha256(expectedPayload), result.ballotHash());

        // Commitment hash uses the existing semantics (proof phrase + payload).
        assertEquals(sha256("ballot_commitment\n" + PROOF + "\n" + expectedPayload),
                result.ballotCommitmentHash());

        // Council selections stored in canonical (contest) order: alice, dave, grace.
        List<String> council = rows.stream()
                .filter(r -> r.officeKey().equals(COUNCIL))
                .map(AnonymousBallotContestResponse::candidateKey)
                .toList();
        assertEquals(List.of(ALICE, DAVE, GRACE), council);
    }

    @Test
    void invalidBallotWritesNothing(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "invalid");
        Poll poll = ctx.poll;
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();

        // Council approval exceeds maxSelections (4 > 3): rejected before any write.
        LinkedElectionBallot invalid = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(ALICE, DAVE, ERIN, FRANK))));

        assertThrows(IllegalArgumentException.class, () ->
                ctx.service.storeLinkedOfficesBallot(poll, def, invalid, "id-1", "JAVA", null, null, PROOF, T));

        assertEquals(0, countRows(ctx.dbm, "participation_records", poll.pollId()));
        assertEquals(0, countRows(ctx.dbm, "anonymous_ballots", poll.pollId()));
        assertEquals(0, countAllResponses(ctx.dbm));
    }

    @Test
    void duplicateParticipantRollsBackSecondStore(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "dup");
        Poll poll = ctx.poll;
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();

        LinkedElectionBallot ballot = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(ALICE, DAVE, GRACE))));

        ctx.service.storeLinkedOfficesBallot(poll, def, ballot, "same-id", "JAVA", null, null, PROOF, T);

        // Second store for the same participant must be rejected and roll back.
        assertThrows(PollServiceException.class, () ->
                ctx.service.storeLinkedOfficesBallot(
                        poll, def, ballot, "same-id", "JAVA", null, null, "other-proof-words-here", T));

        assertEquals(1, countRows(ctx.dbm, "participation_records", poll.pollId()));
        assertEquals(1, countRows(ctx.dbm, "anonymous_ballots", poll.pollId()));
        assertEquals(6, countAllResponses(ctx.dbm));
    }

    @Test
    void reconstructedBallotReproducesOriginalCanonicalPayload(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "reconstruct");
        Poll poll = ctx.poll;
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();

        LinkedElectionBallot ballot = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(BOB, ALICE, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(GRACE, ALICE, DAVE))));

        LinkedBallotStorageService.LinkedBallotStorageResult result =
                ctx.service.storeLinkedOfficesBallot(poll, def, ballot, "id-1", "JAVA", null, null, PROOF, T);

        List<AnonymousBallotContestResponse> rows =
                new AnonymousBallotContestResponseDao(ctx.dbm)
                        .findResponsesByAnonymousBallotId(result.anonymousBallotId());

        LinkedElectionBallot reconstructed = new LinkedBallotReconstructor().reconstruct(def, rows);
        String reconstructedPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(poll, def, reconstructed, T);

        assertEquals(result.canonicalPayload(), reconstructedPayload);
        assertEquals(result.ballotHash(), sha256(reconstructedPayload));
    }

    @Test
    void approvalInputOrderDoesNotChangePayloadOrStoredRowOrder(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "approval-order");
        Poll poll = ctx.poll;
        ElectionDefinition def = LinkedStorageTestFixtures.mayorCouncil();

        LinkedElectionBallot ballotA = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(GRACE, ALICE, DAVE))));
        LinkedElectionBallot ballotB = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, GRACE, ALICE))));

        // Same canonical payload regardless of submitted approval order.
        assertEquals(
                canonicalizer.canonicalLinkedOfficesBallotPayload(poll, def, ballotA, T),
                canonicalizer.canonicalLinkedOfficesBallotPayload(poll, def, ballotB, T));

        // Store both (distinct identities + timestamps so ballot_hash does not collide)
        // and confirm identical stored canonical row order.
        AnonymousBallotContestResponseDao dao = new AnonymousBallotContestResponseDao(ctx.dbm);
        var resA = ctx.service.storeLinkedOfficesBallot(poll, def, ballotA, "id-A", "JAVA", null, null, "proof-a-words", T);
        var resB = ctx.service.storeLinkedOfficesBallot(
                poll, def, ballotB, "id-B", "JAVA", null, null, "proof-b-words", T.plusMillis(1));

        List<String> orderA = dao.findResponsesByAnonymousBallotId(resA.anonymousBallotId()).stream()
                .map(AnonymousBallotContestResponse::candidateKey).toList();
        List<String> orderB = dao.findResponsesByAnonymousBallotId(resB.anonymousBallotId()).stream()
                .map(AnonymousBallotContestResponse::candidateKey).toList();

        assertEquals(orderA, orderB);
        assertEquals(List.of(ALICE, BOB, CAROL, ALICE, DAVE, GRACE), orderA);
    }

    @Test
    void stvCouncilStoresRankedRowsAndRoundTrips(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "stv-store");
        Poll poll = ctx.poll;
        ElectionDefinition def = LinkedStorageTestFixtures.mayorStvCouncil();

        // Council is STV (ranked): a ranked Council response must store as RANKED rows
        // with 1-based rank positions, exactly like IRV ranked responses.
        LinkedElectionBallot ballot = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new RankedContestVote(COUNCIL, List.of(GRACE, DAVE, ERIN, ALICE))));

        LinkedBallotStorageService.LinkedBallotStorageResult result =
                ctx.service.storeLinkedOfficesBallot(poll, def, ballot, "id-stv", "JAVA", null, null, PROOF, T);

        List<AnonymousBallotContestResponse> council =
                new AnonymousBallotContestResponseDao(ctx.dbm)
                        .findResponsesByAnonymousBallotId(result.anonymousBallotId()).stream()
                        .filter(r -> r.officeKey().equals(COUNCIL))
                        .toList();

        // Ranked: 4 rows, all RANKED, rank positions 1..4 preserving voter order, no selection order.
        assertEquals(4, council.size());
        for (AnonymousBallotContestResponse row : council) {
            assertEquals(AnonymousBallotContestResponse.TYPE_RANKED, row.responseType());
        }
        assertEquals(List.of(GRACE, DAVE, ERIN, ALICE),
                council.stream().map(AnonymousBallotContestResponse::candidateKey).toList());
        assertEquals(List.of(1, 2, 3, 4),
                council.stream().map(AnonymousBallotContestResponse::rankPosition).toList());

        // Reconstruction reproduces the original canonical payload and hash byte-for-byte.
        List<AnonymousBallotContestResponse> rows =
                new AnonymousBallotContestResponseDao(ctx.dbm)
                        .findResponsesByAnonymousBallotId(result.anonymousBallotId());
        LinkedElectionBallot reconstructed = new LinkedBallotReconstructor().reconstruct(def, rows);
        String reconstructedPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(poll, def, reconstructed, T);
        assertEquals(result.canonicalPayload(), reconstructedPayload);
        assertEquals(result.ballotHash(), sha256(reconstructedPayload));
    }

    // --- helpers --------------------------------------------------------------

    private record Ctx(DatabaseManager dbm, LinkedBallotStorageService service, Poll poll) {
    }

    private static Ctx setup(Path tempDir, String name) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve(name + ".db"));
        new SchemaInitializer(dbm).initialize();
        PollDao pollDao = new PollDao(dbm);

        long pollId;
        try (Connection connection = dbm.getConnection()) {
            pollId = pollDao.insertPoll(
                    connection, LinkedStorageTestFixtures.linkedOfficesPoll(0, "linked-" + name),
                    "tester", "DEFAULT", "{}");
        }
        Poll poll = LinkedStorageTestFixtures.linkedOfficesPoll(pollId, "linked-" + name);
        return new Ctx(dbm, new LinkedBallotStorageService(dbm), poll);
    }

    private static int countRows(DatabaseManager dbm, String table, long pollId) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE poll_id = ?")) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countAllResponses(DatabaseManager dbm) throws Exception {
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM anonymous_ballot_contest_responses");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
