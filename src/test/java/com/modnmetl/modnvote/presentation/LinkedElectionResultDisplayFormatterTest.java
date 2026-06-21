package com.modnmetl.modnvote.presentation;

import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.results.CandidateResult;
import com.modnmetl.modnvote.domain.election.results.CandidateTally;
import com.modnmetl.modnvote.domain.election.results.ContestResult;
import com.modnmetl.modnvote.domain.election.results.IrvRoundResult;
import com.modnmetl.modnvote.domain.election.results.LinkedElectionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Bukkit-free linked-offices result formatter.
 *
 * <p>The formatter receives only anonymous office/candidate keys and counts, so it
 * cannot emit voter identity. These tests pin the rendered content and guard the
 * privacy boundary against a future regression that threads identity through.
 */
class LinkedElectionResultDisplayFormatterTest {

    private static LinkedElectionResult sampleResult() {
        ContestResult exec = new ContestResult(
                "office_exec", "Exec", CountingMethod.IRV, 1,
                List.of("alice"),
                List.of(
                        new CandidateResult("alice", 3, true, false, null),
                        new CandidateResult("bob", 2, false, false, 2),
                        new CandidateResult("carol", 0, false, false, 1)),
                List.of(),
                1,
                List.of(
                        new IrvRoundResult(1, List.of(
                                new CandidateTally("alice", 2),
                                new CandidateTally("bob", 1),
                                new CandidateTally("carol", 1)),
                                List.of("alice", "bob", "carol"), "carol", null, 0),
                        new IrvRoundResult(2, List.of(
                                new CandidateTally("alice", 3),
                                new CandidateTally("bob", 2)),
                                List.of("alice", "bob"), null, "alice", 1)),
                List.of());

        ContestResult board = new ContestResult(
                "office_board", "Board", CountingMethod.APPROVAL_TOP_N, 2,
                List.of("dave", "erin"),
                List.of(
                        new CandidateResult("dave", 4, true, false, null),
                        new CandidateResult("erin", 3, true, false, null),
                        new CandidateResult("alice", 0, false, true, null)),
                List.of("alice"),
                0,
                List.of(),
                List.of());

        return new LinkedElectionResult(7L, "Town Election", true, 5, 0,
                List.of(exec, board), List.of());
    }

    @Test
    void outputIncludesOfficesWinnersAndTallies() {
        String joined = String.join("\n", LinkedElectionResultDisplayFormatter.formatInGame(sampleResult()));

        assertTrue(joined.contains("#7"), joined);
        assertTrue(joined.contains("Town Election"), joined);
        // Offices
        assertTrue(joined.contains("office_exec"), joined);
        assertTrue(joined.contains("office_board"), joined);
        // Winners
        assertTrue(joined.contains("Winners: §falice"), joined);
        assertTrue(joined.contains("dave, erin"), joined);
        // Tallies / scores
        assertTrue(joined.contains("alice§7: §f3"), joined);
        assertTrue(joined.contains("dave§7: §f4"), joined);
        // IRV round detail and dependency exclusion
        assertTrue(joined.contains("Round 1"), joined);
        assertTrue(joined.contains("Eliminated: §fcarol"), joined);
        assertTrue(joined.contains("Excluded by dependency"), joined);
    }

    @Test
    void incompleteResultAndIssuesAreShown() {
        LinkedElectionResult incomplete = new LinkedElectionResult(
                9L, "Broken Election", false, 0, 2,
                List.of(),
                List.of("Election dependency rules form a cycle; ..."));

        String joined = String.join("\n", LinkedElectionResultDisplayFormatter.formatInGame(incomplete));

        assertTrue(joined.contains("incomplete"), joined);
        assertTrue(joined.contains("Skipped ballots: §f2"), joined);
        assertTrue(joined.contains("cycle"), joined);
    }

    @Test
    void unresolvedApprovalTieIsShownAndTiedCandidatesNotElected() {
        // Council: 4 seats; space,rooster elected; katie,metta,mort tied for 2 seats.
        ContestResult council = new ContestResult(
                "office_council", "Council", CountingMethod.APPROVAL_TOP_N, 4,
                List.of("space", "rooster"),
                List.of(
                        new CandidateResult("space", 3, true, false, null, false),
                        new CandidateResult("rooster", 3, true, false, null, false),
                        new CandidateResult("katie", 2, false, false, null, true),
                        new CandidateResult("metta", 2, false, false, null, true),
                        new CandidateResult("mort", 2, false, false, null, true),
                        new CandidateResult("fitzy", 1, false, false, null, false)),
                List.of(),
                0,
                List.of(),
                List.of("Office 'office_council' has an approval tie at the seat cutoff: 2 seat(s) remain unresolved among tied candidates [katie, metta, mort]."),
                false, 2, List.of("katie", "metta", "mort"));

        LinkedElectionResult result = new LinkedElectionResult(
                11L, "Pineton Election", false, 6, 0, List.of(council), List.of());

        String joined = String.join("\n", LinkedElectionResultDisplayFormatter.formatInGame(result));

        assertTrue(joined.contains("Winners: §fspace, rooster"), joined);
        assertTrue(joined.contains("unresolved: §f2"), joined);
        assertTrue(joined.contains("Tied candidates: §fkatie, metta, mort"), joined);
        assertTrue(joined.contains("runoff/admin resolution"), joined);
        assertTrue(joined.contains("katie§7: §f2 §c(tied — unresolved)"), joined);
        // Tied candidates must never be rendered as elected.
        assertFalse(joined.contains("katie§7: §f2 §a(elected)"), joined);
        assertFalse(joined.contains("mort§7: §f2 §a(elected)"), joined);
        // Whole-result incompleteness is surfaced.
        assertTrue(joined.contains("incomplete"), joined);
    }

    @Test
    void outputNeverContainsIdentityMaterial() {
        String joined = String.join("\n", LinkedElectionResultDisplayFormatter.formatInGame(sampleResult()));

        for (String forbidden : List.of(
                "uuid", "11111111-2222", "PlayerName", "ip-hash",
                "floodgate", "participation-token", "receipt", "proof")) {
            assertFalse(joined.toLowerCase().contains(forbidden.toLowerCase()),
                    "result output leaked forbidden value '" + forbidden + "': " + joined);
        }
    }
}
