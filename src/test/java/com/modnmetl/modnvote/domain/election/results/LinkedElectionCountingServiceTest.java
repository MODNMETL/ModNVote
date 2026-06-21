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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String C6 = "c6";

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
    void approvalCutoffTieIsNotBrokenByContestOrder() {
        // 2 seats; c1=c2=c3=2, c4=0. The tied score group {c1,c2,c3} (3) exceeds the
        // 2 remaining seats, so candidate order must NOT elect c1,c2 — all three are
        // unresolved and no seat is filled by the count.
        ElectionDefinition def = singleApproval(BOARD, 2, List.of(C1, C2, C3, C4));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            addApproval(ballots, def, BOARD, List.of(C1, C2, C3));
        }

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(), board.winners(), "no candidate may be elected by order at the cutoff");
        assertFalse(board.complete());
        assertEquals(2, board.unresolvedSeatCount());
        assertEquals(List.of(C1, C2, C3), board.unresolvedCandidateKeys());
        assertFalse(result.complete(), "a cutoff tie makes the whole election incomplete");
        assertTrue(board.candidateResults().stream()
                        .filter(c -> List.of(C1, C2, C3).contains(c.candidateKey()))
                        .allMatch(c -> c.unresolved() && !c.elected()),
                "tied candidates must be flagged unresolved and not elected");
    }

    @Test
    void approvalCutoffTieWithMoreTiedThanRemainingSeats() {
        // 4 seats; scores c1=3,c2=3,c3=2,c4=2,c5=2,c6=1.
        // {c1,c2}@3 fit -> elected; {c3,c4,c5}@2 (3) exceed the 2 remaining seats -> unresolved.
        ElectionDefinition def = singleApproval(BOARD, 4, List.of(C1, C2, C3, C4, C5, C6));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(C1, 3); scores.put(C2, 3);
        scores.put(C3, 2); scores.put(C4, 2); scores.put(C5, 2);
        scores.put(C6, 1);
        giveScores(ballots, def, BOARD, scores);

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2), board.winners());
        assertFalse(board.complete());
        assertEquals(2, board.unresolvedSeatCount());
        assertEquals(List.of(C3, C4, C5), board.unresolvedCandidateKeys());
        assertFalse(result.complete());
        // c6 is below the unresolved group and is neither elected nor unresolved.
        CandidateResult c6 = candidate(board, C6);
        assertFalse(c6.elected());
        assertFalse(c6.unresolved());
    }

    @Test
    void approvalTieExactlyFitsRemainingSeats() {
        // 4 seats; scores 5,4,3,3,1 -> {c3,c4}@3 exactly fills the last 2 seats. Complete.
        ElectionDefinition def = singleApproval(BOARD, 4, List.of(C1, C2, C3, C4, C5));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(C1, 5); scores.put(C2, 4); scores.put(C3, 3); scores.put(C4, 3); scores.put(C5, 1);
        giveScores(ballots, def, BOARD, scores);

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2, C3, C4), board.winners());
        assertTrue(board.complete());
        assertEquals(0, board.unresolvedSeatCount());
        assertEquals(List.of(), board.unresolvedCandidateKeys());
        assertTrue(result.complete());
    }

    @Test
    void approvalTieBelowCutoffDoesNotBlock() {
        // 4 seats; scores 5,4,3,2,1,1 -> top 4 are clear; the c5=c6=1 tie is below the
        // cutoff and never affects the filled seats.
        ElectionDefinition def = singleApproval(BOARD, 4, List.of(C1, C2, C3, C4, C5, C6));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(C1, 5); scores.put(C2, 4); scores.put(C3, 3); scores.put(C4, 2);
        scores.put(C5, 1); scores.put(C6, 1);
        giveScores(ballots, def, BOARD, scores);

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2, C3, C4), board.winners());
        assertTrue(board.complete());
        assertEquals(0, board.unresolvedSeatCount());
        assertTrue(result.complete());
    }

    @Test
    void approvalTieForFinalSeatIsUnresolved() {
        // 4 seats; scores 5,4,3,2,2 -> A,B,C clear; {c4,c5}@2 tie for the single last seat.
        ElectionDefinition def = singleApproval(BOARD, 4, List.of(C1, C2, C3, C4, C5));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(C1, 5); scores.put(C2, 4); scores.put(C3, 3); scores.put(C4, 2); scores.put(C5, 2);
        giveScores(ballots, def, BOARD, scores);

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2, C3), board.winners());
        assertFalse(board.complete());
        assertEquals(1, board.unresolvedSeatCount());
        assertEquals(List.of(C4, C5), board.unresolvedCandidateKeys());
        assertFalse(result.complete());
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

    // --- STV ------------------------------------------------------------------

    @Test
    void stvElectsFourWinnersUnderQuota() {
        // 4 seats; c1..c4 each get 3 first preferences (= quota), c5 gets 1.
        // Total valid = 13, Droop quota = floor(13/5)+1 = 3. The four front-runners
        // each meet quota and are elected; c5 is not.
        ElectionDefinition def = singleStv(BOARD, 4, List.of(C1, C2, C3, C4, C5));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 3, List.of(C1));
        addRankedTimes(ballots, def, BOARD, 3, List.of(C2));
        addRankedTimes(ballots, def, BOARD, 3, List.of(C3));
        addRankedTimes(ballots, def, BOARD, 3, List.of(C4));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C5));

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2, C3, C4), board.winners());
        assertTrue(board.complete());
        assertEquals("3.000000", board.stv().quota());
        assertFalse(candidate(board, C5).elected());
    }

    @Test
    void stvTransfersSurplusToElectRemainingSeats() {
        // 3 seats; 5×(c1>c2), 2×c3, 1×c4. Total = 8, quota = floor(8/4)+1 = 3.
        // c1 reaches quota (5), its surplus 2 transfers to c2 at 0.4 -> c2 = 2.0.
        // c4 is eliminated (exhausts), then c2 and c3 fill the last two seats.
        ElectionDefinition def = singleStv(BOARD, 3, List.of(C1, C2, C3, C4));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 5, List.of(C1, C2));
        addRankedTimes(ballots, def, BOARD, 2, List.of(C3));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C4));

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2, C3), board.winners());
        assertTrue(board.complete());
        assertEquals("3.000000", board.stv().quota());
        // Round 1 elected c1 and described a surplus transfer.
        StvRoundResult round1 = board.stv().rounds().get(0);
        assertEquals(List.of(C1), round1.electedThisRound());
        assertTrue(round1.summary().contains("surplus"), () -> "round 1 summary: " + round1.summary());
        // c4 was eliminated at some point.
        assertTrue(board.stv().rounds().stream().anyMatch(r -> C4.equals(r.eliminatedCandidateKey())),
                "c4 must be eliminated");
    }

    @Test
    void stvEliminationTransferElectsContinuingCandidate() {
        // 2 seats; 2×c1, 4×c2, 1×(c3>c1), 1×(c4>c1). Total = 8, quota = floor(8/3)+1 = 3.
        // c2 reaches quota and is elected (surplus exhausts on bullet votes). Then c3
        // and c4 tie lowest but the elimination is not seat-deciding, so c4 (latest in
        // order) is eliminated and its ballot transfers to c1 -> c1 reaches quota and
        // is elected. The eliminated candidate's transfer changes the outcome.
        ElectionDefinition def = singleStv(BOARD, 2, List.of(C1, C2, C3, C4));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 2, List.of(C1));
        addRankedTimes(ballots, def, BOARD, 4, List.of(C2));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C3, C1));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C4, C1));

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C2, C1), board.winners(), "c2 first by quota, then c1 via elimination transfer");
        assertTrue(board.complete());
        assertTrue(candidate(board, C1).elected());
        assertTrue(board.stv().rounds().stream().anyMatch(r -> C4.equals(r.eliminatedCandidateKey())),
                "c4 must be eliminated and transfer to c1");
    }

    @Test
    void stvExhaustsBallotsWithNoContinuingPreference() {
        // 2 seats; 6×c1 (bullet), 2×c2, 1×c3. Total = 9, quota = floor(9/3)+1 = 4.
        // c1's surplus (2) has nowhere to go (bullet votes) and exhausts; c3 is then
        // eliminated and also exhausts. Exhausted value must be strictly positive.
        ElectionDefinition def = singleStv(BOARD, 2, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 6, List.of(C1));
        addRankedTimes(ballots, def, BOARD, 2, List.of(C2));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C3));

        ContestResult board = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2), board.winners());
        assertTrue(board.complete());
        assertEquals("4.000000", board.stv().quota());
        assertTrue(Double.parseDouble(board.stv().exhaustedValue()) > 0.0,
                () -> "expected positive exhausted value: " + board.stv().exhaustedValue());
    }

    @Test
    void stvAppliesMayorWinnerExclusionBeforeCouncilCount() {
        // Mayor (IRV, 1 seat) excludes its winner ("vradow") from Council (STV, 4 seats).
        // After exclusion, Council eligibles are space/rooster/katie/metta/mort/fitzy.
        // Two vradow>space ballots transfer to space at count time (exclusion at count).
        String mayor = "office_mayor";
        String council = "office_council";
        String vradow = "vradow";
        String runnerUp = "mayor_runner";
        String space = "space";
        String rooster = "rooster";
        String katie = "katie";
        String metta = "metta";
        String mort = "mort";
        String fitzy = "fitzy";

        ContestDefinition mayorContest = new ContestDefinition(
                mayor, "Mayor", CountingMethod.IRV, 1, null, false, List.of(vradow, runnerUp));
        ContestDefinition councilContest = new ContestDefinition(
                council, "Council", CountingMethod.STV, 4, null, false,
                List.of(space, rooster, katie, metta, mort, fitzy, vradow));
        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition(vradow, "Vradow", List.of(mayor, council)),
                new CandidateDefinition(runnerUp, "Runner", List.of(mayor)),
                new CandidateDefinition(space, "Space", List.of(council)),
                new CandidateDefinition(rooster, "Rooster", List.of(council)),
                new CandidateDefinition(katie, "Katie", List.of(council)),
                new CandidateDefinition(metta, "Metta", List.of(council)),
                new CandidateDefinition(mort, "Mort", List.of(council)),
                new CandidateDefinition(fitzy, "Fitzy", List.of(council)));
        List<OfficeDependencyRule> deps = List.of(
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, mayor, council));
        ElectionDefinition def = new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL,
                List.of(mayorContest, councilContest), candidates, deps);

        List<LinkedElectionBallot> ballots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ballots.add(new LinkedElectionBallot(def, List.<ContestVote>of(
                    new RankedContestVote(mayor, List.of(vradow)))));
        }
        ballots.add(new LinkedElectionBallot(def, List.<ContestVote>of(
                new RankedContestVote(mayor, List.of(runnerUp)))));
        // Council ballots: bullets plus two vradow>space (vradow excluded -> count for space).
        addRankedTimes(ballots, def, council, 5, List.of(space));
        addRankedTimes(ballots, def, council, 5, List.of(rooster));
        addRankedTimes(ballots, def, council, 3, List.of(katie));
        addRankedTimes(ballots, def, council, 3, List.of(metta));
        addRankedTimes(ballots, def, council, 2, List.of(mort));
        addRankedTimes(ballots, def, council, 1, List.of(fitzy));
        addRankedTimes(ballots, def, council, 2, List.of(vradow, space));

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult councilResult = result.findContest(council).orElseThrow();

        assertEquals(List.of(vradow), result.findContest(mayor).orElseThrow().winners());
        assertTrue(councilResult.excludedCandidateKeys().contains(vradow),
                "Mayor winner must be excluded from Council before the STV count");
        assertFalse(councilResult.winners().contains(vradow), "excluded candidate cannot win Council");
        assertEquals(List.of(space, rooster, katie, metta), councilResult.winners());
        assertTrue(councilResult.complete());
        assertTrue(result.complete());
    }

    @Test
    void stvIsDeterministicAcrossRepeatedRuns() {
        ElectionDefinition def = singleStv(BOARD, 3, List.of(C1, C2, C3, C4));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 5, List.of(C1, C2));
        addRankedTimes(ballots, def, BOARD, 2, List.of(C3));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C4));

        ContestResult first = counting.count(poll(), def, ballots).findContest(BOARD).orElseThrow();
        ContestResult second = new LinkedElectionCountingService().count(poll(), def, ballots)
                .findContest(BOARD).orElseThrow();

        assertEquals(first.winners(), second.winners());
        assertEquals(first.stv().quota(), second.stv().quota());
        assertEquals(first.stv().exhaustedValue(), second.stv().exhaustedValue());
        assertEquals(first.stv().rounds().size(), second.stv().rounds().size());
        for (int i = 0; i < first.stv().rounds().size(); i++) {
            assertEquals(first.stv().rounds().get(i).summary(), second.stv().rounds().get(i).summary());
            assertEquals(first.stv().rounds().get(i).tallies().size(),
                    second.stv().rounds().get(i).tallies().size());
        }
    }

    @Test
    void stvEliminationTieForFinalSeatsIsUnresolvedNotBrokenByOrder() {
        // 2 seats; c1=c2=c3 each 2 bullet votes. Total = 6, quota = floor(6/3)+1 = 3.
        // No one reaches quota and all three are tied lowest; eliminating any of them
        // decides the final seats, so the contest is left unresolved rather than
        // dropping a candidate by definition order.
        ElectionDefinition def = singleStv(BOARD, 2, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 2, List.of(C1));
        addRankedTimes(ballots, def, BOARD, 2, List.of(C2));
        addRankedTimes(ballots, def, BOARD, 2, List.of(C3));

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(), board.winners(), "no candidate may be elected by order in a seat-deciding tie");
        assertFalse(board.complete());
        assertEquals(2, board.unresolvedSeatCount());
        assertEquals(List.of(C1, C2, C3), board.unresolvedCandidateKeys());
        assertFalse(result.complete());
        for (String tied : List.of(C1, C2, C3)) {
            assertTrue(candidate(board, tied).unresolved(), tied + " must be unresolved");
            assertFalse(candidate(board, tied).elected(), tied + " must not be elected");
        }
    }

    @Test
    void stvQuotaTieWithinRemainingSeatsElectsAllTiedNotByOrder() {
        // 2 seats; c1=3, c2=3 (tied at quota), c3=1. Total = 7, quota = floor(7/3)+1 = 3.
        // c1 and c2 share the top at-or-above-quota tally. Because the tied group (2)
        // fits the remaining seats (2), BOTH are elected — candidate order only
        // sequences which surplus is processed first, it does not pick a winner and
        // does not drop c2. The contest is complete with no unresolved seats.
        ElectionDefinition def = singleStv(BOARD, 2, List.of(C1, C2, C3));
        List<LinkedElectionBallot> ballots = new ArrayList<>();
        addRankedTimes(ballots, def, BOARD, 3, List.of(C1));
        addRankedTimes(ballots, def, BOARD, 3, List.of(C2));
        addRankedTimes(ballots, def, BOARD, 1, List.of(C3));

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult board = result.findContest(BOARD).orElseThrow();

        assertEquals(List.of(C1, C2), board.winners(), "both candidates tied at quota must be elected");
        assertTrue(board.complete());
        assertEquals(0, board.unresolvedSeatCount());
        assertEquals(List.of(), board.unresolvedCandidateKeys());
        assertEquals("3.000000", board.stv().quota());
        assertTrue(result.complete());
        for (String elected : List.of(C1, C2)) {
            assertTrue(candidate(board, elected).elected(), elected + " must be elected");
            assertFalse(candidate(board, elected).unresolved(), elected + " must not be unresolved");
        }
        assertFalse(candidate(board, C3).elected());
    }

    @Test
    void highestAtOrAboveQuotaGroupCapturesFullTiedQuotaGroupInContestOrder() {
        // Directly exercises the fairness-critical grouping the quota-election guard
        // relies on. Under a strict Droop quota, value conservation makes a tied group
        // larger than the remaining seats unreachable through real ballots — at most
        // `remainingSeats` candidates can sit at or above quota at once — so the guard
        // input is verified here at the unit level: the helper always returns the
        // COMPLETE set of candidates tied at the top at-or-above-quota tally, in contest
        // order, never an order-picked single winner.
        List<String> order = List.of(C1, C2, C3, C4);
        Set<String> continuing = Set.of(C1, C2, C3, C4);
        BigDecimal quota = new BigDecimal("4");

        // Three candidates tied at the top quota tally: the whole group is returned, in
        // contest order. If the seats remaining were fewer than 3, the count would leave
        // them unresolved rather than electing one of them by order.
        assertEquals(List.of(C1, C2, C3),
                counting.highestAtOrAboveQuotaGroup(order, continuing,
                        tally(C1, "5", C2, "5", C3, "5", C4, "2"), quota));

        // Only the single strict leader is the top group; candidates merely at/above
        // quota but below the leader are elected in later rounds, not part of this tie.
        assertEquals(List.of(C1),
                counting.highestAtOrAboveQuotaGroup(order, continuing,
                        tally(C1, "6", C2, "4", C3, "4", C4, "1"), quota));

        // Ties are reported strictly in contest order even when the high candidates are
        // not adjacent in the tally.
        assertEquals(List.of(C2, C4),
                counting.highestAtOrAboveQuotaGroup(order, continuing,
                        tally(C1, "2", C2, "5", C3, "3", C4, "5"), quota));

        // Nobody reaches quota: empty group (the count would eliminate instead).
        assertEquals(List.of(),
                counting.highestAtOrAboveQuotaGroup(order, continuing,
                        tally(C1, "3", C2, "2", C3, "1", C4, "0"), quota));
    }

    private static Map<String, BigDecimal> tally(String k1, String v1, String k2, String v2,
                                                 String k3, String v3, String k4, String v4) {
        Map<String, BigDecimal> tally = new LinkedHashMap<>();
        tally.put(k1, new BigDecimal(v1));
        tally.put(k2, new BigDecimal(v2));
        tally.put(k3, new BigDecimal(v3));
        tally.put(k4, new BigDecimal(v4));
        return tally;
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

    @Test
    void pinetonCouncilApprovalTieLeavesSeatsUnresolved() {
        // Linked offices: Mayor (IRV, 1 seat) excludes its winner from Council
        // (APPROVAL_TOP_N, 4 seats). Reproduces the manual smoke finding:
        // after excluding the Mayor winner ("vradow"), Council scores are
        // Space=3, Rooster=3, Katie=2, Metta=2, Mort=2, Fitzy=1.
        // Space+Rooster are clearly elected; the three 2-vote candidates tie for the
        // two remaining seats and must all be unresolved — Mort must not be silently
        // dropped by candidate definition order.
        String mayor = "office_mayor";
        String council = "office_council";
        String vradow = "vradow";
        String runnerUp = "mayor_runner";
        String space = "space";
        String rooster = "rooster";
        String katie = "katie";
        String metta = "metta";
        String mort = "mort";
        String fitzy = "fitzy";

        ContestDefinition mayorContest = new ContestDefinition(
                mayor, "Mayor", CountingMethod.IRV, 1, null, false, List.of(vradow, runnerUp));
        ContestDefinition councilContest = new ContestDefinition(
                council, "Council", CountingMethod.APPROVAL_TOP_N, 4, 4, false,
                List.of(space, rooster, katie, metta, mort, fitzy, vradow));
        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition(vradow, "Vradow", List.of(mayor, council)),
                new CandidateDefinition(runnerUp, "Runner", List.of(mayor)),
                new CandidateDefinition(space, "Space", List.of(council)),
                new CandidateDefinition(rooster, "Rooster", List.of(council)),
                new CandidateDefinition(katie, "Katie", List.of(council)),
                new CandidateDefinition(metta, "Metta", List.of(council)),
                new CandidateDefinition(mort, "Mort", List.of(council)),
                new CandidateDefinition(fitzy, "Fitzy", List.of(council)));
        List<OfficeDependencyRule> deps = List.of(
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, mayor, council));
        ElectionDefinition def = new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL,
                List.of(mayorContest, councilContest), candidates, deps);

        List<LinkedElectionBallot> ballots = new ArrayList<>();
        // Mayor: vradow wins outright (3 of 4 first preferences).
        for (int i = 0; i < 3; i++) {
            ballots.add(new LinkedElectionBallot(def, List.<ContestVote>of(
                    new RankedContestVote(mayor, List.of(vradow)))));
        }
        ballots.add(new LinkedElectionBallot(def, List.<ContestVote>of(
                new RankedContestVote(mayor, List.of(runnerUp)))));
        // Council approvals giving Space=3, Rooster=3, Katie=2, Metta=2, Mort=2, Fitzy=1.
        Map<String, Integer> councilScores = new LinkedHashMap<>();
        councilScores.put(space, 3);
        councilScores.put(rooster, 3);
        councilScores.put(katie, 2);
        councilScores.put(metta, 2);
        councilScores.put(mort, 2);
        councilScores.put(fitzy, 1);
        giveScores(ballots, def, council, councilScores);

        LinkedElectionResult result = counting.count(poll(), def, ballots);
        ContestResult mayorResult = result.findContest(mayor).orElseThrow();
        ContestResult councilResult = result.findContest(council).orElseThrow();

        assertEquals(List.of(vradow), mayorResult.winners());
        assertTrue(councilResult.excludedCandidateKeys().contains(vradow),
                "Mayor winner must be excluded from Council");
        assertEquals(List.of(space, rooster), councilResult.winners(),
                "only the two clear front-runners are elected");
        assertEquals(2, councilResult.unresolvedSeatCount());
        assertEquals(List.of(katie, metta, mort), councilResult.unresolvedCandidateKeys(),
                "Mort must not be dropped by candidate order — it is unresolved with the others");
        assertFalse(councilResult.complete());
        assertFalse(result.complete());
        // None of the tied candidates may be reported as elected.
        for (String tied : List.of(katie, metta, mort)) {
            assertFalse(candidate(councilResult, tied).elected(), tied + " must not be elected");
            assertTrue(candidate(councilResult, tied).unresolved(), tied + " must be unresolved");
        }
    }

    // --- helpers --------------------------------------------------------------

    private static CandidateResult candidate(ContestResult contest, String candidateKey) {
        return contest.candidateResults().stream()
                .filter(c -> c.candidateKey().equals(candidateKey))
                .findFirst()
                .orElseThrow();
    }

    private static void giveScores(List<LinkedElectionBallot> ballots, ElectionDefinition def,
                                   String officeKey, Map<String, Integer> scores) {
        // Each single-selection approval ballot adds exactly one to one candidate's score,
        // so the resulting tally matches the requested scores precisely.
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                addApproval(ballots, def, officeKey, List.of(entry.getKey()));
            }
        }
    }

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

    private static ElectionDefinition singleStv(String officeKey, int seats, List<String> candidateKeys) {
        ContestDefinition contest = new ContestDefinition(
                officeKey, officeKey, CountingMethod.STV, seats, null, false, candidateKeys);
        return new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(contest),
                candidateDefs(officeKey, candidateKeys), List.of());
    }

    private static void addRankedTimes(List<LinkedElectionBallot> ballots, ElectionDefinition def,
                                       String officeKey, int count, List<String> ranking) {
        for (int i = 0; i < count; i++) {
            addRanked(ballots, def, officeKey, ranking);
        }
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
