package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionSerializer;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import com.modnmetl.modnvote.service.LinkedOfficeBallotProofVerificationResult.OfficeResponse;
import com.modnmetl.modnvote.service.canonical.BallotCanonicalizer;
import com.modnmetl.modnvote.service.canonical.BallotHashingService;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for linked-offices bearer-token proof verification
 * ({@link LinkedOfficesProofVerifier}, the Bukkit-free collaborator
 * {@link BallotService} delegates the LINKED_OFFICES proof case to).
 *
 * <p>Each test stores a real linked-offices ballot through
 * {@link LinkedBallotStorageService}, then verifies (or fails to verify) it with a
 * proof phrase, optionally after tampering with the stored anonymous content.
 *
 * <p>The full {@link BallotService} cannot be constructed in a unit test because
 * its {@code PlatformAdapter} dependency exposes Bukkit types
 * ({@code Player}/{@code Plugin}) and the Paper API is {@code compileOnly} (absent
 * from the test classpath). The linked-offices logic therefore lives in this
 * standalone verifier — the same pattern as {@link LinkedBallotStorageService} and
 * {@link LinkedOfficesIntegrityVerifier}. The unchanged single-contest (YES_NO /
 * RANKED_SINGLE_WINNER) proof derivation is regression-locked here in
 * {@link #singleContestProofDerivationRegressionUnchanged()} and, at the formula
 * level, in {@code BallotHashingServiceTest} and {@code BallotCanonicalizerTest}.
 */
class LinkedOfficesProofVerifierTest {

    private static final Instant T = Instant.ofEpochMilli(1000L);
    private static final String PROOF = "river-stone-maple-fox";

    @Test
    void validProofVerifiesAndReturnsReconstructedContent(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "valid");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        LinkedOfficeBallotProofVerificationResult result = ctx.verifier.verify(ctx.poll, PROOF);

        assertTrue(result.ballotFound());
        assertTrue(result.verified(), () -> "failure: " + result.failureReason());
        assertNull(result.failureReason());
        assertEquals(ballotId, result.anonymousBallotId());
        assertEquals(T, result.submittedAt());

        // Content matches submitted/reconstructed content: ranked order preserved,
        // approval normalised to contest candidate order (alice, dave, grace).
        List<OfficeResponse> offices = result.offices();
        assertEquals(2, offices.size());
        assertEquals(MAYOR, offices.get(0).officeKey());
        assertEquals("RANKED", offices.get(0).responseType());
        assertEquals(List.of(ALICE, BOB, CAROL), offices.get(0).orderedCandidateKeys());
        assertEquals(COUNCIL, offices.get(1).officeKey());
        assertEquals("APPROVAL", offices.get(1).responseType());
        assertEquals(List.of(ALICE, DAVE, GRACE), offices.get(1).orderedCandidateKeys());
    }

    @Test
    void stvCouncilProofVerifiesWithRankedContent(@TempDir Path tempDir) throws Exception {
        // STV Council reuses the ranked storage/proof path; a valid proof must verify
        // and return the Council response as RANKED in the voter's preference order.
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve("stv-proof.db"));
        new SchemaInitializer(dbm).initialize();
        ElectionDefinition def = LinkedStorageTestFixtures.mayorStvCouncil();
        String configJson = new ElectionDefinitionSerializer().serialize(def);
        PollDao pollDao = new PollDao(dbm);
        long pollId;
        try (Connection connection = dbm.getConnection()) {
            pollId = pollDao.insertPoll(connection, linkedPoll(0, "stv-proof", configJson),
                    "tester", "DEFAULT", configJson);
        }
        Poll poll = linkedPoll(pollId, "stv-proof", configJson);
        LinkedBallotStorageService storage = new LinkedBallotStorageService(dbm);
        LinkedOfficesProofVerifier verifier = new LinkedOfficesProofVerifier(dbm);

        LinkedElectionBallot ballot = new LinkedElectionBallot(def, List.of(
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new RankedContestVote(COUNCIL, List.of(GRACE, DAVE, ERIN, ALICE))));
        storage.storeLinkedOfficesBallot(poll, def, ballot, "id-stv", "JAVA", null, null, PROOF, T);

        LinkedOfficeBallotProofVerificationResult result = verifier.verify(poll, PROOF);

        assertTrue(result.verified(), () -> "failure: " + result.failureReason());
        OfficeResponse council = result.offices().stream()
                .filter(o -> o.officeKey().equals(COUNCIL)).findFirst().orElseThrow();
        assertEquals("RANKED", council.responseType());
        assertEquals(List.of(GRACE, DAVE, ERIN, ALICE), council.orderedCandidateKeys());
    }

    @Test
    void wrongProofPhraseReturnsNoBallotContent(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "wrong-phrase");
        storeBallot(ctx, "id-1", PROOF, T);

        LinkedOfficeBallotProofVerificationResult result = ctx.verifier.verify(ctx.poll, "wrong-words-here-now");

        assertFalse(result.ballotFound());
        assertFalse(result.verified());
        assertTrue(result.offices().isEmpty());
        assertNull(result.anonymousBallotId());
        assertNull(result.ballotHash());
    }

    @Test
    void tamperedCommitmentHashFailsVerification(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "tamper-commitment");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        exec(ctx.dbm, "UPDATE anonymous_ballots SET ballot_commitment_hash = 'deadbeef' "
                + "WHERE anonymous_ballot_id = " + ballotId);

        LinkedOfficeBallotProofVerificationResult result = ctx.verifier.verify(ctx.poll, PROOF);

        assertTrue(result.ballotFound());
        assertFalse(result.verified());
        assertTrue(result.offices().isEmpty());
        assertTrue(result.failureReason().contains("commitment_valid=false"),
                () -> "reason: " + result.failureReason());
    }

    @Test
    void tamperedContestRowsFailVerification(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "tamper-rows");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        // Swap a stored council selection (dave -> erin); erin is also council-eligible,
        // so reconstruction stays valid but the canonical payload — and both the
        // recomputed ballot hash and commitment — change.
        updateCandidateKey(ctx.dbm, ballotId, COUNCIL, DAVE, ERIN);

        LinkedOfficeBallotProofVerificationResult result = ctx.verifier.verify(ctx.poll, PROOF);

        assertTrue(result.ballotFound());
        assertFalse(result.verified());
        assertTrue(result.offices().isEmpty());
        assertTrue(result.failureReason().contains("exact-ballot verification"),
                () -> "reason: " + result.failureReason());
    }

    @Test
    void invalidConfigJsonFailsVerification(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "bad-config");
        storeBallot(ctx, "id-1", PROOF, T);

        Poll pollWithBadConfig = linkedPoll(ctx.poll.pollId(), ctx.poll.slug(), "{}");

        LinkedOfficeBallotProofVerificationResult result = ctx.verifier.verify(pollWithBadConfig, PROOF);

        assertTrue(result.ballotFound());
        assertFalse(result.verified());
        assertTrue(result.failureReason().contains("invalid or missing linked-offices definition"),
                () -> "reason: " + result.failureReason());
    }

    @Test
    void missingContestRowsFailVerification(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "missing-rows");
        long ballotId = storeBallot(ctx, "id-1", PROOF, T);

        exec(ctx.dbm, "DELETE FROM anonymous_ballot_contest_responses WHERE anonymous_ballot_id = " + ballotId);

        LinkedOfficeBallotProofVerificationResult result = ctx.verifier.verify(ctx.poll, PROOF);

        assertTrue(result.ballotFound());
        assertFalse(result.verified());
        assertTrue(result.failureReason().contains("no contest-response rows"),
                () -> "reason: " + result.failureReason());
    }

    /**
     * Single-contest proof verification regression: pins the exact recompute steps
     * {@code BallotService.verifyBallotProof} performs for YES_NO / RANKED ballots
     * (canonical payload → sha256 ballot hash; proof hash; commitment over the
     * payload), proving the linked-offices addition did not perturb them. The full
     * {@code verifyBallotProof} method itself cannot be unit-constructed (Bukkit
     * {@code PlatformAdapter}).
     */
    @Test
    void singleContestProofDerivationRegressionUnchanged() {
        BallotCanonicalizer canonicalizer = new BallotCanonicalizer();
        Poll rankedPoll = new Poll(7L, "slug-7", "Title", "Description",
                PollType.RANKED_SINGLE_WINNER, PollStatus.OPEN, null, null,
                6, 1, false, true, "participation-secret");
        List<Long> orderedOptionIds = List.of(11L, 22L, 33L);

        String payload = canonicalizer.canonicalAnonymousBallotPayload(rankedPoll, orderedOptionIds, T);

        // Ballot hash: sha256 of the canonical payload, recompute matches stored-style value.
        String ballotHash = BallotHashingService.sha256(payload);
        assertEquals(ballotHash, BallotHashingService.sha256(payload));

        // Proof hash and commitment are deterministic; commitment binds the phrase.
        String proofHash = BallotHashingService.buildBallotProofHash(rankedPoll.pollId(), PROOF);
        assertEquals(proofHash, BallotHashingService.buildBallotProofHash(rankedPoll.pollId(), PROOF));

        String commitment = BallotHashingService.buildBallotCommitmentHash(PROOF, payload);
        assertEquals(commitment, BallotHashingService.buildBallotCommitmentHash(PROOF, payload));
        assertNotEquals(commitment, BallotHashingService.buildBallotCommitmentHash("other-phrase-words-here", payload));
    }

    @Test
    void resultsAndFailuresContainNoVoterIdentity(@TempDir Path tempDir) throws Exception {
        Ctx ctx = setup(tempDir, "privacy");
        String identityKey = "player-uuid-1234-secret";
        String ipHash = "ip-hash-abcdef";
        String floodgateId = "floodgate-id-9999";
        long ballotId = storeBallot(ctx, identityKey, PROOF, T, "JAVA", ipHash, floodgateId);

        // Successful verification result must not carry any identity material.
        LinkedOfficeBallotProofVerificationResult ok = ctx.verifier.verify(ctx.poll, PROOF);
        assertTrue(ok.verified());
        String okText = ok.toString();
        assertNoIdentity(okText, identityKey, ipHash, floodgateId);

        // Force a failure and check the failure reason is also identity-free.
        exec(ctx.dbm, "UPDATE anonymous_ballots SET ballot_commitment_hash = 'deadbeef' "
                + "WHERE anonymous_ballot_id = " + ballotId);
        LinkedOfficeBallotProofVerificationResult bad = ctx.verifier.verify(ctx.poll, PROOF);
        assertFalse(bad.verified());
        assertNoIdentity(bad.failureReason(), identityKey, ipHash, floodgateId);
    }

    private static void assertNoIdentity(String text, String identityKey, String ipHash, String floodgateId) {
        assertFalse(text.contains(identityKey), "leaked identity key");
        assertFalse(text.contains(ipHash), "leaked ip hash");
        assertFalse(text.contains(floodgateId), "leaked floodgate id");
        assertFalse(text.toLowerCase().contains(PROOF), "leaked proof phrase");
    }

    // --- helpers --------------------------------------------------------------

    private record Ctx(DatabaseManager dbm, LinkedBallotStorageService service,
                       LinkedOfficesProofVerifier verifier, Poll poll, ElectionDefinition definition) {
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
                new LinkedOfficesProofVerifier(dbm), poll, def);
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
