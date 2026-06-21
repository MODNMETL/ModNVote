package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.ALICE;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.BOB;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.COUNCIL;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.DAVE;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.ERIN;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.FRANK;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.GRACE;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedElectionBallotValidatorTest {

    private final LinkedElectionBallotValidator validator = new LinkedElectionBallotValidator();

    @Test
    void validMayorRankingPasses() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                LinkedElectionFixtures.validMayorRanking());

        BallotValidationResult result = validator.validate(ballot);

        assertTrue(result.valid(), () -> "expected valid, got: " + result.issues());
    }

    @Test
    void validCouncilApprovalPasses() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                LinkedElectionFixtures.validCouncilApproval());

        BallotValidationResult result = validator.validate(ballot);

        assertTrue(result.valid(), () -> "expected valid, got: " + result.issues());
    }

    @Test
    void fullValidBallotPasses() {
        BallotValidationResult result = validator.validate(LinkedElectionFixtures.validBallot());

        assertTrue(result.valid(), () -> "expected valid, got: " + result.issues());
    }

    @Test
    void invalidOfficeIsReported() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote("treasurer", List.of(BOB)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.UNKNOWN_OFFICE));
    }

    @Test
    void invalidCandidateIsReported() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(BOB, "nobody")));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.UNKNOWN_CANDIDATE));
    }

    @Test
    void duplicateRankedCandidateIsReported() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(BOB, ALICE, BOB)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.DUPLICATE_CANDIDATE));
    }

    @Test
    void duplicateApprovalCandidateIsReported() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new ApprovalContestVote(COUNCIL, List.of(DAVE, DAVE)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.DUPLICATE_CANDIDATE));
    }

    @Test
    void approvalExceedingMaxSelectionsIsReported() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK, GRACE)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.EXCEEDS_MAX_SELECTIONS));
    }

    @Test
    void ineligibleCandidateIsReported() {
        // Dave is eligible for Council only; ranking him for Mayor is ineligible.
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(BOB, DAVE)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.INELIGIBLE_CANDIDATE));
    }

    @Test
    void wrongVoteTypeIsReported() {
        // Mayor is IRV; supplying an approval response is the wrong shape.
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new ApprovalContestVote(MAYOR, List.of(BOB)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.WRONG_VOTE_TYPE));
    }

    @Test
    void validStvCouncilRankingPasses() {
        // STV is ranked like IRV: a ranked Council response is the correct shape.
        LinkedElectionBallot ballot = LinkedElectionFixtures.stvBallotOf(
                new RankedContestVote(COUNCIL, List.of(DAVE, ERIN, FRANK, ALICE)));

        BallotValidationResult result = validator.validate(ballot);

        assertTrue(result.valid(), () -> "expected valid, got: " + result.issues());
    }

    @Test
    void approvalResponseToStvOfficeIsWrongVoteType() {
        // Council is STV (ranked); an approval response is the wrong shape.
        LinkedElectionBallot ballot = LinkedElectionFixtures.stvBallotOf(
                new ApprovalContestVote(COUNCIL, List.of(DAVE, ERIN)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.WRONG_VOTE_TYPE));
    }

    @Test
    void duplicateResponseForSameOfficeIsReported() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(BOB)),
                new RankedContestVote(MAYOR, List.of(ALICE)));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.DUPLICATE_RESPONSE));
    }

    @Test
    void unresolvedDependencyReferenceIsReported() {
        ElectionDefinition base = LinkedElectionFixtures.mayorCouncil();
        ElectionDefinition broken = new ElectionDefinition(
                base.model(),
                base.contests(),
                base.candidates(),
                List.of(new com.modnmetl.modnvote.domain.election.OfficeDependencyRule(
                        com.modnmetl.modnvote.domain.election.OfficeDependencyType.EXCLUDE_WINNERS,
                        MAYOR, "ghost")));
        LinkedElectionBallot ballot = new LinkedElectionBallot(broken,
                List.of(LinkedElectionFixtures.validMayorRanking()));

        BallotValidationResult result = validator.validate(ballot);

        assertFalse(result.valid());
        assertTrue(result.hasCode(BallotValidationCode.UNRESOLVED_DEPENDENCY));
    }

    @Test
    void doesNotThrowAndReportsAllIssuesDeterministically() {
        LinkedElectionBallot ballot = LinkedElectionFixtures.ballotOf(
                new RankedContestVote(MAYOR, List.of(BOB, "nobody", BOB)));

        // No exception even with multiple problems.
        BallotValidationResult first = validator.validate(ballot);
        BallotValidationResult second = validator.validate(ballot);

        List<BallotValidationCode> firstCodes = first.issues().stream()
                .map(BallotValidationIssue::code).toList();
        List<BallotValidationCode> secondCodes = second.issues().stream()
                .map(BallotValidationIssue::code).toList();
        assertEquals(firstCodes, secondCodes);
        assertFalse(first.valid());
    }
}
