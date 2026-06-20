package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;
import com.modnmetl.modnvote.domain.election.OfficeDependencyType;

import java.util.List;

/**
 * Reusable Mayor/Council fixtures for linked-election execution-model tests.
 *
 * <p>The names are illustrative only; nothing in the production code hardcodes
 * "Mayor" or "Council". This fixture exercises both counting methods (IRV for
 * Mayor, APPROVAL_TOP_N for Council) and an EXCLUDE_WINNERS dependency from
 * Mayor to Council (Alice stands for both, so a Mayor winner could be excluded
 * from Council at count time in a later tranche).
 */
final class LinkedElectionFixtures {

    static final String MAYOR = "mayor";
    static final String COUNCIL = "council";

    static final String ALICE = "alice";
    static final String BOB = "bob";
    static final String CAROL = "carol";
    static final String DAVE = "dave";
    static final String ERIN = "erin";
    static final String FRANK = "frank";
    static final String GRACE = "grace";

    private LinkedElectionFixtures() {
    }

    /**
     * @return a valid Mayor (IRV, 1 seat) + Council (APPROVAL_TOP_N, 3 seats,
     * maxSelections 3) election with an EXCLUDE_WINNERS dependency Mayor → Council
     */
    static ElectionDefinition mayorCouncil() {
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

    static RankedContestVote validMayorRanking() {
        return new RankedContestVote(MAYOR, List.of(BOB, ALICE, CAROL));
    }

    static ApprovalContestVote validCouncilApproval() {
        return new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK));
    }

    static LinkedElectionBallot validBallot() {
        return new LinkedElectionBallot(mayorCouncil(),
                List.of(validMayorRanking(), validCouncilApproval()));
    }

    static LinkedElectionBallot ballotOf(ContestVote... votes) {
        return new LinkedElectionBallot(mayorCouncil(), List.of(votes));
    }
}
