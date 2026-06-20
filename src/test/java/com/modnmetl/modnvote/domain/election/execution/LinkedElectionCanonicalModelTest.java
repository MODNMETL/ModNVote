package com.modnmetl.modnvote.domain.election.execution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.ALICE;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.BOB;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.CAROL;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.COUNCIL;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.DAVE;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.ERIN;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.FRANK;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkedElectionCanonicalModelTest {

    private final LinkedElectionCanonicalModel model = new LinkedElectionCanonicalModel();

    @Test
    void contestOrderingIsStableAndFollowsDefinition() {
        assertEquals(List.of(MAYOR, COUNCIL),
                model.contestOrder(LinkedElectionFixtures.mayorCouncil()));
    }

    @Test
    void candidateOrderingFollowsContestDefinition() {
        assertEquals(List.of(ALICE, BOB, CAROL),
                model.candidateOrder(LinkedElectionFixtures.mayorCouncil(), MAYOR));
    }

    @Test
    void responsesAreOrderedByContestRegardlessOfBallotOrder() {
        // Supply responses in reverse contest order.
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                LinkedElectionFixtures.validCouncilApproval(),
                LinkedElectionFixtures.validMayorRanking());

        CanonicalBallot canonical = model.canonicalize(ballot);

        assertEquals(List.of(MAYOR, COUNCIL),
                canonical.responses().stream().map(CanonicalContestResponse::officeKey).toList());
    }

    @Test
    void rankedResponsePreservesVoterOrder() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(CAROL, BOB, ALICE)));

        CanonicalBallot canonical = model.canonicalize(ballot);

        assertEquals(List.of(CAROL, BOB, ALICE),
                canonical.responses().get(0).orderedCandidateKeys());
    }

    @Test
    void approvalSelectionsAreNormalisedToContestOrder() {
        // Submitted out of contest order; canonical form must match contest order.
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new ApprovalContestVote(COUNCIL, List.of(FRANK, DAVE, ERIN)));

        CanonicalBallot canonical = model.canonicalize(ballot);

        assertEquals(List.of(DAVE, ERIN, FRANK),
                canonical.responses().get(0).orderedCandidateKeys());
    }

    @Test
    void canonicalizationIsDeterministicAcrossInputOrderings() {
        LinkedElectionBallot a = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(BOB, ALICE, CAROL)),
                new ApprovalContestVote(COUNCIL, List.of(ERIN, DAVE, FRANK)));
        LinkedElectionBallot b = LinkedElectionFixtures.ballotOf(
                new ApprovalContestVote(COUNCIL, List.of(FRANK, ERIN, DAVE)),
                new RankedContestVote(MAYOR, List.of(BOB, ALICE, CAROL)));

        assertEquals(model.canonicalize(a), model.canonicalize(b));
    }
}
