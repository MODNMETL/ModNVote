package com.modnmetl.modnvote.domain.election.results;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the deterministic linked-offices counting calculator.
 *
 * <p>{@link LinkedElectionCountingService} is Bukkit-free and database-free, so it
 * is exercised directly here with hand-built definitions and in-memory ballots.
 * Office and candidate keys are deliberately generic ("office_*", "c*") to prove
 * the counting is not tied to any specific office name.
 */
class LinkedElectionCountingServiceTest {

    private static final String EXEC = "office_exec";
    private static final String BOARD = "office_board";

    private static final String C1 = "c1";
    private static final String C2 = "c2";
    private static final String C3 = "c3";
    private static final String C4 = "c4";
    private static final String C5 = "c5";

    private final LinkedElectionCountingService counting = new LinkedElectionCountingService();

    // --- IRV ------------------------------------------------------------------

    @Test
    void irvFirstRoundMajorityElectsWinner() {
        ElectionDefinition def = singleIrv(EXEC, 1, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRanked(ballots, def, EXEC, List.of(C1));
        addRanked(ballots, def, EXEC, List.of(C1));
        addRanked(ballots, def, EXEC, List.of(C1));
        addRanked(ballots, def, EXEC, List.of(C2));
        addRanked(ballots, def, EXEC, List.of(C3));

        ContestResult exec = counting.count(poll(), def, ballots).findContest(EXEC).orElseThrow();

        assertEquals(List.of(C1), exec.winners());
        assertEquals(1, exec.rounds().size(), "majority on first round needs a single round");
        assertEquals(C1, exec.rounds().get(0).winnerCandidateKey());
    }

    @Test
    void irvEliminationAndTransferElectsWinner() {
        ElectionDefinition def = singleIrv(EXEC, 1, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRanked(ballots, def, EXEC, List.of(C1, C2));
        addRanked(ballots, def, EXEC, List.of(C1, C2));
        addRanked(ballots, def, EXEC, List.of(C2, C1));
        addRanked(ballots, def, EXEC, List.of(C2, C1));
        addRanked(ballots, def, EXEC, List.of(C3, C1));

        ContestResult exec = counting.count(poll(), def, ballots).findContest(EXEC).orElseThrow();

        // Round 1: c1=2, c2=2, c3=1 -> eliminate c3. Round 2: c3 transfers to c1 -> c1=3 majority.
        assertEquals(2, exec.rounds().size());
        assertEquals(C3, exec.rounds().get(0).eliminatedCandidateKey());
        assertEquals(List.of(C1), exec.winners());
        assertEquals(C1, exec.rounds().get(1).winnerCandidateKey());
    }

    @Test
    void irvExhaustedBallotsAreCountedAndDoNotBlockMajority() {
        ElectionDefinition def = singleIrv(EXEC, 1, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            addRanked(ballots, def, EXEC, List.of(C1));
        }
        for (int i = 0; i < 2; i++) {
            addRanked(ballots, def, EXEC, List.of(C2)); // bullet vote, no transfer
        }
        for (int i = 0; i < 2; i++) {
            addRanked(ballots, def, EXEC, List.of(C3)); // bullet vote, no transfer
        }

        ContestResult exec = counting.count(poll(), def, ballots).findContest(EXEC).orElseThrow();

        // Round 1: c1=3,c2=2,c3=2 -> eliminate latest tied (c3). Round 2: c3's 2 ballots exhaust;
        // active = 5, c1=3 > 2.5 majority.
        assertEquals(List.of(C1), exec.winners());
        IrvRoundResult finalRound = exec.rounds().get(exec.rounds().size() - 1);
        assertEquals(2, finalRound.exhaustedBallots());
        assertEquals(2, exec.exhaustedBallots());
    }

    @Test
    void irvTieBreakEliminatesLatestInContestOrder() {
        ElectionDefinition def = singleIrv(EXEC, 1, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRanked(ballots, def, EXEC, List.of(C1));
        addRanked(ballots, def, EXEC, List.of(C1));
        addRanked(ballots, def, EXEC, List.of(C2));
        addRanked(ballots, def, EXEC, List.of(C3));

        ContestResult exec = counting.count(poll(), def, ballots).findContest(EXEC).orElseThrow();

        // Round 1: c1=2,c2=1,c3=1 -> tie at lowest between c2 and c3; c3 is later in order,
        // so c3 is eliminated and c2 (earlier) survives.
        assertEquals(C3, exec.rounds().get(0).eliminatedCandidateKey());
    }

    // --- Approval -------------------------------------------------------------

    @Test
    void approvalTopNElectsHighestScores() {
        ElectionDefinition def = singleApproval(BOARD, 3, List.of(C1, C2, C3, C4, C5));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        // c1=5, c2=4, c3=3, c4=2, c5=1
        for (int i = 0; i < 5; i++) {
            addApproval(ballots, def, BOARD, approvalsFor(i, C1, C2, C3, C4, C5));
        }

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2, C3), board.winners());
    }

    @Test
    void approvalTiesBrokenByContestOrder() {
        ElectionDefinition def = singleApproval(BOARD, 2, List.of(C1, C2, C3, C4));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        // c1=c2=c3=2, c4=0 -> top 2 by contest order are c1, c2.
        for (int i = 0; i < 2; i++) {
            addApproval(ballots, def, BOARD, List.of(C1, C2, C3));
        }

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2), board.winners());
    }

    @Test
    void approvalReportsIssueWhenFewerCandidatesThanSeats() {
        ElectionDefinition def = singleApproval(BOARD, 4, List.of(C1, C2));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addApproval(ballots, def, BOARD, List.of(C1, C2));

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2), board.winners());
        assertTrue(board.issues().stream().anyMatch(i -> i.contains("seat")),
                () -> "expected fewer-candidates-than-seats issue: " + board.issues());
    }

    // --- Dependency -----------------------------------------------------------

    @Test
    void excludeWinnersCountsSourceFirstAndExcludesWinnerFromDependent() {
        // src: IRV 1 seat among c1,c2,c3 ; dst: approval 2 seats among c1,c4,c5.
        ContestDefinition src = new ContestDefinition(EXEC, "Exec", CountingMethod.IRV, 1, null, false,
                List.of(C1, C2, C3));
        ContestDefinition dst = new ContestDefinition(BOARD, "Board", CountingMethod.APPROVAL_TOP_N, 2, 3, false,
                List.of(C1, C4, C5));
        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition(C1, "C1", List.of(EXEC, BOARD)),
                new CandidateDefinition(C2, "C2", List.of(EXEC)),
                new CandidateDefinition(C3, "C3", List.of(EXEC)),
                new CandidateDefinition(C4, "C4", List.of(BOARD)),
                new CandidateDefinition(C5, "C5", List.of(BOARD)));
        List<OfficeDependencyRule> deps = List.of(
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, EXEC, BOARD));
        ElectionDefinition def = new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(src, dst), candidates, deps);

        List<LinkedElectionBallot> ballots = new ArrayList<>();
        // c1 wins exec outright, and is also approved heavily for board.
        for (int i = 0; i < 3; i++) {
            ballots.add(new LinkedElectionBallot(def, List.of(
                    new RankedContestVote(EXEC, List.of(C1)),
                    new ApprovalContestVote(BOARD, List.of(C1, C4)))));
        }
        ballots.add(new LinkedElectionBallot(def, List.of(
                new RankedContestVote(EXEC, List.of(C2)),
                new ApprovalContestVote(BOARD, List.of(C5)))));

        LinkedElectionResult result = counting.count(poll(), def, ballots);

        // Counting order must place the source office before the dependent office.
        assertEquals(EXEC, result.contestResults().get(0).officeKey());
        assertEquals(BOARD, result.contestResults().get(1).officeKey());

        ContestResult exec = result.findContest(EXEC).orElseThrow();
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1), exec.winners());
        // c1 won exec, so c1 is excluded from board even though approved 3 times.
        assertTrue(board.excludedCandidateKeys().contains(C1));
        assertFalse(board.winners().contains(C1), "exec winner must not win board");
        // Remaining eligible board candidates c4(3), c5(1) -> winners c4, c5.
        assertEquals(List.of(C4, C5), board.winners());
        assertTrue(result.complete());
    }

    @Test
    void dependencyCycleIsReportedAndDoesNotPretendSuccess() {
        ContestDefinition a = new ContestDefinition(EXEC, "A", CountingMethod.APPROVAL_TOP_N, 1, 1, false,
                List.of(C1, C2));
        ContestDefinition b = new ContestDefinition(BOARD, "B", CountingMethod.APPROVAL_TOP_N, 1, 1, false,
                List.of(C3, C4));
        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition(C1, "C1", List.of(EXEC)),
                new CandidateDefinition(C2, "C2", List.of(EXEC)),
                new CandidateDefinition(C3, "C3", List.of(BOARD)),
                new CandidateDefinition(C4, "C4", List.of(BOARD)));
        List<OfficeDependencyRule> deps = List.of(
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, EXEC, BOARD),
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, BOARD, EXEC));
        ElectionDefinition def = new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(a, b), candidates, deps);

        List<LinkedElectionBallot> ballots = new ArrayList<>();
        ballots.add(new LinkedElectionBallot(def, List.of(
                new ApprovalContestVote(EXEC, List.of(C1)),
                new ApprovalContestVote(BOARD, List.of(C3)))));

        LinkedElectionResult result = counting.count(poll(), def, ballots);

        assertFalse(result.complete(), "a cyclic dependency graph must not be reported as complete");
        assertTrue(result.issues().stream().anyMatch(i -> i.toLowerCase().contains("cycle")),
                () -> "expected a cycle issue: " + result.issues());
        // Both contests are still counted (in definition order) without exclusions.
        assertEquals(2, result.contestResults().size());
    }

    // --- helpers --------------------------------------------------------------

    private static List<String> approvalsFor(int ballotIndex, String... ordered) {
        // ballotIndex 0..4 approves a decreasing prefix so c1 gets 5, c2 gets 4, ... c5 gets 1.
        List<String> approvals = new ArrayList<>();
        for (int i = 0; i < ordered.length - ballotIndex; i++) {
            approvals.add(ordered[i]);
        }
        return approvals;
    }

    private static ElectionDefinition singleIrv(String officeKey, int seats, List<String> candidateKeys) {
        ContestDefinition contest = new ContestDefinition(
                officeKey, officeKey, CountingMethod.IRV, seats, null, false, candidateKeys);
        return new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(contest),
                candidateDefs(officeKey, candidateKeys), List.of());
    }

    private static ElectionDefinition singleApproval(String officeKey, int seats, List<String> candidateKeys) {
        ContestDefinition contest = new ContestDefinition(
                officeKey, officeKey, CountingMethod.APPROVAL_TOP_N, seats, candidateKeys.size(), false, candidateKeys);
        return new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(contest),
                candidateDefs(officeKey, candidateKeys), List.of());
    }

    private static List<CandidateDefinition> candidateDefs(String officeKey, List<String> candidateKeys) {
        List<CandidateDefinition> defs = new ArrayList<>();
        for (String key : candidateKeys) {
            defs.add(new CandidateDefinition(key, key, List.of(officeKey)));
        }
        return defs;
    }

    private static void addRanked(List<LinkedElectionBallot> ballots, ElectionDefinition def,
                                  String officeKey, List<String> ranking) {
        ballots.add(new LinkedElectionBallot(def, List.<ContestVote>of(new RankedContestVote(officeKey, ranking))));
    }

    private static void addApproval(List<LinkedElectionBallot> ballots, ElectionDefinition def,
                                    String officeKey, List<String> approvals) {
        ballots.add(new LinkedElectionBallot(def, List.<ContestVote>of(new ApprovalContestVote(officeKey, approvals))));
    }

    private static Poll poll() {
        return new Poll(1L, "linked-counting", "Linked Counting", "desc",
                PollType.LINKED_OFFICES, PollStatus.CLOSED, null, null,
                1, 1, false, true, "secret-1", "{}");
    }
}
