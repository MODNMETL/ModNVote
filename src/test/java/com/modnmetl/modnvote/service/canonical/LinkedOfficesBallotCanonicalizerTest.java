package com.modnmetl.modnvote.service.canonical;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;
import com.modnmetl.modnvote.domain.election.OfficeDependencyType;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden-output and stability tests for the linked-offices canonical payload
 * produced by {@link BallotCanonicalizer#canonicalLinkedOfficesBallotPayload}.
 *
 * <p>The fixture mirrors the Tranche 2E Mayor/Council fixture (Mayor = IRV,
 * Council = APPROVAL_TOP_N, EXCLUDE_WINNERS Mayor -> Council). It is rebuilt here
 * because that fixture is package-private to the execution-model package.
 *
 * <p>Note on the spec's illustrative examples: those put "bob" in a Council
 * approval, but bob is eligible for Mayor only, so such a ballot is rejected by
 * validation before any payload is produced (see
 * {@link #invalidLinkedOfficesBallotIsRejectedBeforeCanonicalization()}). These
 * tests therefore exercise approval-order normalisation with Council-eligible
 * candidates, which is the behaviour the spec intends.
 */
class LinkedOfficesBallotCanonicalizerTest {

    private static final Instant FIXED_SUBMITTED_AT = Instant.ofEpochMilli(1000L);

    private static final String MAYOR = "mayor";
    private static final String COUNCIL = "council";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    private static final String DAVE = "dave";
    private static final String ERIN = "erin";
    private static final String FRANK = "frank";
    private static final String GRACE = "grace";

    private static final String PARTICIPATION_SECRET = "super-secret-participation-token-value";

    private final BallotCanonicalizer canonicalizer = new BallotCanonicalizer();

    // --- Fixture ---------------------------------------------------------

    private static ElectionDefinition mayorCouncil() {
        ContestDefinition mayor = new ContestDefinition(
                MAYOR, "Mayor", CountingMethod.IRV, 1, null, false,
                List.of(ALICE, BOB, CAROL));
        ContestDefinition council = new ContestDefinition(
                COUNCIL, "Council", CountingMethod.APPROVAL_TOP_N, 3, 3, false,
                List.of(ALICE, DAVE, ERIN, FRANK, GRACE));

        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition(ALICE, "Alice", List.of(MAYOR, COUNCIL)),
                new CandidateDefinition(BOB, "Bob", List.of(MAYOR)),
                new CandidateDefinition(CAROL, "Carol", List.of(MAYOR)),
                new CandidateDefinition(DAVE, "Dave", List.of(COUNCIL)),
                new CandidateDefinition(ERIN, "Erin", List.of(COUNCIL)),
                new CandidateDefinition(FRANK, "Frank", List.of(COUNCIL)),
                new CandidateDefinition(GRACE, "Grace", List.of(COUNCIL)));

        List<OfficeDependencyRule> dependencies = List.of(
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, MAYOR, COUNCIL));

        return new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(mayor, council), candidates, dependencies);
    }

    private static Poll linkedOfficesPoll(long pollId) {
        return new Poll(
                pollId,
                "slug-" + pollId,
                "Title " + pollId,
                "Description",
                PollType.LINKED_OFFICES,
                PollStatus.DRAFT,
                null,
                null,
                1,
                1,
                false,
                true,
                PARTICIPATION_SECRET
        );
    }

    private static LinkedElectionBallot ballotOf(ElectionDefinition definition, ContestVote... votes) {
        return new LinkedElectionBallot(definition, List.of(votes));
    }

    // --- 1. Golden payload ----------------------------------------------

    @Test
    void linkedOfficesCanonicalPayloadMatchesGoldenString() {
        ElectionDefinition definition = mayorCouncil();
        // Council approval submitted in non-canonical order; all council-eligible.
        LinkedElectionBallot ballot = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(GRACE, ALICE, DAVE)));

        String payload = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(99L), definition, ballot, FIXED_SUBMITTED_AT);

        String expected =
                "poll_id=99\n"
                        + "poll_type=LINKED_OFFICES\n"
                        + "election_model=LINKED_OFFICES\n"
                        + "submitted_at=1000\n"
                        + "rule_snapshot_version=linked_offices_v1\n"
                        + "contest_count=2\n"
                        + "contest=mayor;method=IRV;type=RANKED;candidates=alice,bob,carol\n"
                        + "contest=council;method=APPROVAL_TOP_N;type=APPROVAL;candidates=alice,dave,grace";

        assertEquals(expected, payload);
    }

    // --- 2. Determinism --------------------------------------------------

    @Test
    void identicalContentFromDifferentListInstancesProducesIdenticalPayload() {
        ElectionDefinition definition = mayorCouncil();

        LinkedElectionBallot a = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));
        // Distinct list instances, same content.
        LinkedElectionBallot b = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));

        String first = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(1L), definition, a, FIXED_SUBMITTED_AT);
        String second = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(1L), definition, b, FIXED_SUBMITTED_AT);

        assertEquals(first, second);
    }

    // --- 3. Contest order normalization ---------------------------------

    @Test
    void contestResponseInputOrderDoesNotAffectPayload() {
        ElectionDefinition definition = mayorCouncil();

        LinkedElectionBallot mayorFirst = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));
        LinkedElectionBallot councilFirst = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)),
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)));

        String fromMayorFirst = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(5L), definition, mayorFirst, FIXED_SUBMITTED_AT);
        String fromCouncilFirst = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(5L), definition, councilFirst, FIXED_SUBMITTED_AT);

        assertEquals(fromMayorFirst, fromCouncilFirst);
        // And contests appear in definition order regardless of submission order.
        int mayorIdx = fromCouncilFirst.indexOf("contest=mayor;");
        int councilIdx = fromCouncilFirst.indexOf("contest=council;");
        assertTrue(mayorIdx >= 0 && councilIdx >= 0);
        assertTrue(mayorIdx < councilIdx, "mayor contest must canonicalize before council");
    }

    // --- 4. Approval order normalization --------------------------------

    @Test
    void approvalSelectionInputOrderDoesNotAffectPayload() {
        ElectionDefinition definition = mayorCouncil();

        LinkedElectionBallot scrambled = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(GRACE, DAVE, ALICE)));
        LinkedElectionBallot ordered = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(ALICE, DAVE, GRACE)));

        String fromScrambled = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(8L), definition, scrambled, FIXED_SUBMITTED_AT);
        String fromOrdered = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(8L), definition, ordered, FIXED_SUBMITTED_AT);

        assertEquals(fromOrdered, fromScrambled);
        // Canonical candidate order follows the contest's defined candidate order.
        assertTrue(fromScrambled.contains(
                "contest=council;method=APPROVAL_TOP_N;type=APPROVAL;candidates=alice,dave,grace"));
    }

    // --- 5. Ranked order significance -----------------------------------

    @Test
    void rankedPreferenceOrderChangesPayload() {
        ElectionDefinition definition = mayorCouncil();

        LinkedElectionBallot first = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)));
        LinkedElectionBallot second = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(BOB, ALICE, CAROL)));

        String firstPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(2L), definition, first, FIXED_SUBMITTED_AT);
        String secondPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(2L), definition, second, FIXED_SUBMITTED_AT);

        assertNotEquals(firstPayload, secondPayload);
    }

    // --- 6. Approval set significance -----------------------------------

    @Test
    void changingApprovalSelectionSetChangesPayload() {
        ElectionDefinition definition = mayorCouncil();

        LinkedElectionBallot first = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));
        LinkedElectionBallot second = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, GRACE)));

        String firstPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(3L), definition, first, FIXED_SUBMITTED_AT);
        String secondPayload = canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(3L), definition, second, FIXED_SUBMITTED_AT);

        assertNotEquals(firstPayload, secondPayload);
    }

    // --- 7. Invalid ballot rejected -------------------------------------

    @Test
    void invalidLinkedOfficesBallotIsRejectedBeforeCanonicalization() {
        ElectionDefinition definition = mayorCouncil();

        // Unknown candidate.
        LinkedElectionBallot unknownCandidate = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, "ghost")));
        assertThrows(IllegalArgumentException.class, () ->
                canonicalizer.canonicalLinkedOfficesBallotPayload(
                        linkedOfficesPoll(4L), definition, unknownCandidate, FIXED_SUBMITTED_AT));

        // Too many council approvals (maxSelections = 3).
        LinkedElectionBallot tooMany = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(ALICE, DAVE, ERIN, FRANK)));
        assertThrows(IllegalArgumentException.class, () ->
                canonicalizer.canonicalLinkedOfficesBallotPayload(
                        linkedOfficesPoll(4L), definition, tooMany, FIXED_SUBMITTED_AT));

        // Ineligible candidate: bob (Mayor-only) in a Council approval — exactly the
        // spec's illustrative example, which validation correctly refuses.
        LinkedElectionBallot ineligible = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(GRACE, ALICE, DAVE, BOB)));
        assertThrows(IllegalArgumentException.class, () ->
                canonicalizer.canonicalLinkedOfficesBallotPayload(
                        linkedOfficesPoll(4L), definition, ineligible, FIXED_SUBMITTED_AT));
    }

    @Test
    void nonLinkedOfficesPollIsRejected() {
        ElectionDefinition definition = mayorCouncil();
        LinkedElectionBallot ballot = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)));

        Poll yesNoPoll = new Poll(
                7L, "slug-7", "Title", "Description",
                PollType.YES_NO, PollStatus.DRAFT, null, null,
                1, 1, false, true, PARTICIPATION_SECRET);

        assertThrows(IllegalArgumentException.class, () ->
                canonicalizer.canonicalLinkedOfficesBallotPayload(
                        yesNoPoll, definition, ballot, FIXED_SUBMITTED_AT));
    }

    // --- 8. Identity independence ---------------------------------------

    @Test
    void canonicalPayloadContainsNoVoterIdentity() {
        ElectionDefinition definition = mayorCouncil();
        LinkedElectionBallot ballot = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));

        String payload = assertDoesNotThrow(() -> canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(6L), definition, ballot, FIXED_SUBMITTED_AT));

        // The method signature accepts no player UUID/name/IP/session/token, and the
        // poll's participation secret must never leak into the hash input.
        assertFalse(payload.contains(PARTICIPATION_SECRET), "payload must not contain participation secret");
        assertFalse(payload.toLowerCase().contains("uuid"), "payload must not contain a UUID field");
        assertFalse(payload.toLowerCase().contains("participation"), "payload must not contain participation data");
    }

    // --- F. Hash helper behaviour ---------------------------------------

    @Test
    void sha256OfLinkedPayloadIsDeterministicAndContentSensitive() {
        ElectionDefinition definition = mayorCouncil();

        LinkedElectionBallot base = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));
        // Same intent, different input orderings (response + approval order).
        LinkedElectionBallot reordered = ballotOf(definition,
                new ApprovalContestVote(COUNCIL, List.of(FRANK, DAVE, ERIN)),
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)));
        // Changed ranked order.
        LinkedElectionBallot changedRank = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(BOB, ALICE, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK)));
        // Changed approval set.
        LinkedElectionBallot changedSet = ballotOf(definition,
                new RankedContestVote(MAYOR, List.of(ALICE, BOB, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, GRACE)));

        String hashBase = sha256(canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(10L), definition, base, FIXED_SUBMITTED_AT));
        String hashReordered = sha256(canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(10L), definition, reordered, FIXED_SUBMITTED_AT));
        String hashChangedRank = sha256(canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(10L), definition, changedRank, FIXED_SUBMITTED_AT));
        String hashChangedSet = sha256(canonicalizer.canonicalLinkedOfficesBallotPayload(
                linkedOfficesPoll(10L), definition, changedSet, FIXED_SUBMITTED_AT));

        // Input order (response order, approval selection order) does not change the hash.
        assertEquals(hashBase, hashReordered);
        // Ranked order and approval set both change the hash.
        assertNotEquals(hashBase, hashChangedRank);
        assertNotEquals(hashBase, hashChangedSet);
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
