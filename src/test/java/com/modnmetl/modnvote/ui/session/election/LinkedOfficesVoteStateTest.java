package com.modnmetl.modnvote.ui.session.election;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Bukkit-free {@link LinkedOfficesVoteState}: the heart of the
 * linked-offices voting GUI. These exercise per-office selection rules, ballot
 * construction, submit-readiness, and the rule that dependency outcomes never
 * remove candidates at cast time.
 */
class LinkedOfficesVoteStateTest {

    // Generic Mayor(IRV,1)/Council(APPROVAL_TOP_N,3 seats,max 3) fixture with an
    // EXCLUDE_WINNERS dependency Mayor -> Council. Names are illustrative only.
    private static final String MAYOR = "mayor";
    private static final String COUNCIL = "council";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    private static final String DAVE = "dave";
    private static final String ERIN = "erin";
    private static final String FRANK = "frank";
    private static final String GRACE = "grace";

    private static ElectionDefinition mayorCouncil() {
        ContestDefinition mayor = new ContestDefinition(
                MAYOR, "Mayor", CountingMethod.IRV, 1, null, false, List.of(ALICE, BOB, CAROL));
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

    @Test
    void rankedOfficeKeepsSelectionOrderAndTogglesOff() {
        LinkedOfficesVoteState state = new LinkedOfficesVoteState(mayorCouncil());

        assertTrue(state.toggle(MAYOR, BOB));
        assertTrue(state.toggle(MAYOR, ALICE));
        assertTrue(state.toggle(MAYOR, CAROL));
        assertEquals(List.of(BOB, ALICE, CAROL), state.selectionsFor(MAYOR));
        assertEquals(1, state.rankOf(MAYOR, BOB));
        assertEquals(2, state.rankOf(MAYOR, ALICE));

        // Removing the first preference keeps the order of the remainder.
        assertTrue(state.toggle(MAYOR, BOB));
        assertEquals(List.of(ALICE, CAROL), state.selectionsFor(MAYOR));
        assertEquals(1, state.rankOf(MAYOR, ALICE));
        assertEquals(-1, state.rankOf(MAYOR, BOB));
    }

    @Test
    void approvalOfficeRespectsMaxSelectionsAndDeselection() {
        LinkedOfficesVoteState state = new LinkedOfficesVoteState(mayorCouncil());

        assertTrue(state.toggle(COUNCIL, DAVE));
        assertTrue(state.toggle(COUNCIL, ERIN));
        assertTrue(state.toggle(COUNCIL, GRACE));
        assertEquals(3, state.selectionCount(COUNCIL));

        // Fourth approval is refused because maxSelections is 3 (no change).
        assertFalse(state.toggle(COUNCIL, FRANK));
        assertEquals(3, state.selectionCount(COUNCIL));
        assertFalse(state.isSelected(COUNCIL, FRANK));

        // Deselecting frees a slot.
        assertTrue(state.toggle(COUNCIL, DAVE));
        assertTrue(state.toggle(COUNCIL, FRANK));
        assertEquals(List.of(ERIN, GRACE, FRANK), state.selectionsFor(COUNCIL));
    }

    @Test
    void buildsBallotFromSelectionsInDefinitionOrder() {
        LinkedOfficesVoteState state = new LinkedOfficesVoteState(mayorCouncil());
        state.toggle(MAYOR, ALICE);
        state.toggle(MAYOR, BOB);
        state.toggle(COUNCIL, DAVE);
        state.toggle(COUNCIL, GRACE);

        LinkedElectionBallot ballot = state.buildBallot();
        List<ContestVote> votes = ballot.contestVotes();
        assertEquals(2, votes.size());
        assertTrue(votes.get(0) instanceof RankedContestVote);
        assertEquals(List.of(ALICE, BOB), ((RankedContestVote) votes.get(0)).orderedCandidateKeys());
        assertTrue(votes.get(1) instanceof ApprovalContestVote);
        assertEquals(List.of(DAVE, GRACE), ((ApprovalContestVote) votes.get(1)).selectedCandidateKeys());
    }

    @Test
    void abstainOfficesAreOmittedAndSubmittabilityTracksRequiredOffices() {
        LinkedOfficesVoteState state = new LinkedOfficesVoteState(mayorCouncil());
        assertFalse(state.isSubmittable(), "empty ballot is not submittable");

        state.toggle(MAYOR, ALICE);
        // Council does not allow abstain in this fixture, so it must be addressed.
        assertFalse(state.isSubmittable());

        state.toggle(COUNCIL, DAVE);
        assertTrue(state.isSubmittable());
        assertTrue(state.validate().valid());
    }

    @Test
    void dependencyDoesNotHideSourceOfficeWinnersAtVoteTime() {
        LinkedOfficesVoteState state = new LinkedOfficesVoteState(mayorCouncil());

        // Alice is eligible for Mayor and (despite the EXCLUDE_WINNERS dependency)
        // remains offered for Council at cast time. Exclusion is a count-time outcome.
        assertTrue(state.eligibleCandidates(MAYOR).contains(ALICE));
        assertTrue(state.eligibleCandidates(COUNCIL).contains(ALICE));

        state.toggle(MAYOR, ALICE);
        assertTrue(state.toggle(COUNCIL, ALICE), "Alice may be approved for Council even if ranked for Mayor");
        assertTrue(state.isSelected(COUNCIL, ALICE));
    }

    @Test
    void rejectsIneligibleCandidateForOffice() {
        LinkedOfficesVoteState state = new LinkedOfficesVoteState(mayorCouncil());
        // Bob is only eligible for Mayor.
        assertThrows(IllegalArgumentException.class, () -> state.toggle(COUNCIL, BOB));
        assertThrows(IllegalArgumentException.class, () -> state.toggle("unknown-office", ALICE));
    }
}
