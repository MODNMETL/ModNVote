package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.results.CandidateResult;
import com.modnmetl.modnvote.domain.election.results.CandidateTally;
import com.modnmetl.modnvote.domain.election.results.ContestResult;
import com.modnmetl.modnvote.domain.election.results.IrvRoundResult;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link LinkedElectionWitnessPayloadFormatter}. These touch
 * no Bukkit type and no database; they assert the witness payload includes
 * office results, winners, tallies, IRV rounds, dependency exclusions and
 * issues, renders deterministically, handles incomplete/skipped results, and
 * never emits voter identity or proof material.
 */
class LinkedElectionWitnessPayloadFormatterTest {

    private static final int MAX = 1024;

    private static Poll poll(PollStatus status, Instant closesAt) {
        return new Poll(7L, "linked-poll", "Town Election", "Annual election",
                PollType.LINKED_OFFICES, status, null, closesAt,
                1, 3, false, true, "secret", "{}");
    }

    private static LinkedElectionResult sampleResult(boolean complete, int skipped) {
        ContestResult mayor = new ContestResult(
                "mayor", "Mayor", CountingMethod.IRV, 1,
                List.of("alice"),
                List.of(
                        new CandidateResult("alice", 5, true, false, null),
                        new CandidateResult("bob", 3, false, false, 2),
                        new CandidateResult("carol", 2, false, false, 1)),
                List.of(),
                0,
                List.of(
                        new IrvRoundResult(1,
                                List.of(new CandidateTally("alice", 4),
                                        new CandidateTally("bob", 3),
                                        new CandidateTally("carol", 2)),
                                List.of("alice", "bob", "carol"),
                                "carol", null, 0),
                        new IrvRoundResult(2,
                                List.of(new CandidateTally("alice", 5),
                                        new CandidateTally("bob", 3)),
                                List.of("alice", "bob"),
                                null, "alice", 1)),
                List.of("Mayor counted cleanly."));

        ContestResult council = new ContestResult(
                "council", "Council", CountingMethod.APPROVAL_TOP_N, 2,
                List.of("dave", "erin"),
                List.of(
                        new CandidateResult("dave", 4, true, false, null),
                        new CandidateResult("erin", 3, true, false, null),
                        new CandidateResult("alice", 0, false, true, null)),
                List.of("alice"),
                0,
                List.of(),
                List.of());

        return new LinkedElectionResult(7L, "Town Election", complete, 5, skipped,
                List.of(mayor, council),
                complete ? List.of() : List.of("Definition had a structural problem."));
    }

    @Test
    void includesOfficeResultsWinnersTalliesRoundsExclusionsAndIssues() {
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> fields =
                LinkedElectionWitnessPayloadFormatter.buildFields(
                        poll(PollStatus.CLOSED, Instant.parse("2026-06-21T12:00:00Z")),
                        sampleResult(true, 0), Instant.parse("2026-06-21T12:00:00Z"), MAX);

        String all = render(fields);

        // Summary fields.
        assertTrue(all.contains("Poll ID=#7"), all);
        assertTrue(all.contains("Type=LINKED_OFFICES"), all);
        assertTrue(all.contains("Status=CLOSED"), all);
        assertTrue(all.contains("Closed=2026-06-21T12:00:00Z"), all);
        assertTrue(all.contains("Result=Complete"), all);
        assertTrue(all.contains("Counted Ballots=5"), all);
        assertTrue(all.contains("Skipped Ballots=0"), all);
        assertTrue(all.contains("Offices=2"), all);

        // Office headings and bodies.
        assertTrue(all.contains("Office: Mayor (mayor)"), all);
        assertTrue(all.contains("Method: IRV | Seats: 1"), all);
        assertTrue(all.contains("Winners: alice"), all);
        assertTrue(all.contains("alice: 5 (elected)"), all);
        // IRV rounds present.
        assertTrue(all.contains("IRV rounds:"), all);
        assertTrue(all.contains("Round 1: alice 4, bob 3, carol 2"), all);
        assertTrue(all.contains("Eliminated: carol"), all);
        assertTrue(all.contains("Round 2: alice 5, bob 3"), all);
        assertTrue(all.contains("Elected: alice"), all);
        // Contest-level issue.
        assertTrue(all.contains("Issue: Mayor counted cleanly."), all);

        // Approval office with dependency exclusion.
        assertTrue(all.contains("Office: Council (council)"), all);
        assertTrue(all.contains("Method: APPROVAL_TOP_N | Seats: 2"), all);
        assertTrue(all.contains("Winners: dave, erin"), all);
        assertTrue(all.contains("Excluded by dependency: alice"), all);
        assertTrue(all.contains("alice (excluded)"), all);
    }

    @Test
    void deterministicAcrossInvocations() {
        Poll p = poll(PollStatus.CLOSED, Instant.parse("2026-06-21T12:00:00Z"));
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> first =
                LinkedElectionWitnessPayloadFormatter.buildFields(p, sampleResult(true, 0),
                        Instant.parse("2026-06-21T12:00:00Z"), MAX);
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> second =
                LinkedElectionWitnessPayloadFormatter.buildFields(p, sampleResult(true, 0),
                        Instant.parse("2026-06-21T12:00:00Z"), MAX);
        assertEquals(render(first), render(second));

        // Offices render in result order: Mayor before Council.
        String all = render(first);
        assertTrue(all.indexOf("Office: Mayor (mayor)") < all.indexOf("Office: Council (council)"), all);
    }

    @Test
    void handlesIncompleteResultAndSkippedBallots() {
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> fields =
                LinkedElectionWitnessPayloadFormatter.buildFields(
                        poll(PollStatus.CLOSED, null), sampleResult(false, 2), null, MAX);

        String all = render(fields);
        assertTrue(all.contains("Result=Incomplete"), all);
        assertTrue(all.contains("Skipped Ballots=2"), all);
        assertTrue(all.contains("Election Issues"), all);
        assertTrue(all.contains("Definition had a structural problem."), all);
        // No close timestamp field when the close time is unavailable.
        assertFalse(all.contains("Closed="), all);
    }

    @Test
    void containsNoIdentityOrProofMaterial() {
        List<LinkedElectionWitnessPayloadFormatter.WitnessField> fields =
                LinkedElectionWitnessPayloadFormatter.buildFields(
                        poll(PollStatus.CLOSED, Instant.now()), sampleResult(true, 0), Instant.now(), MAX);

        String all = render(fields).toLowerCase();
        for (String forbidden : List.of("uuid", "ip-hash", "iphash", "floodgate", "participation",
                "receipt", "proof", "secret", "token", "identity")) {
            assertFalse(all.contains(forbidden), () -> "payload leaked '" + forbidden + "': " + all);
        }
    }

    private static String render(List<LinkedElectionWitnessPayloadFormatter.WitnessField> fields) {
        StringBuilder sb = new StringBuilder();
        for (LinkedElectionWitnessPayloadFormatter.WitnessField field : fields) {
            sb.append(field.name()).append('=').append(field.value()).append('\n');
        }
        return sb.toString();
    }
}
