package com.modnmetl.modnvote.domain.election.results;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;
import com.modnmetl.modnvote.domain.election.OfficeDependencyType;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.DependencyEvaluation;
import com.modnmetl.modnvote.domain.election.execution.ElectionDependencyEvaluator;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic counting and result calculation for a linked-offices election.
 *
 * <p>This is a pure, Bukkit-free, database-free calculator. It accepts an already
 * validated/parsed {@link ElectionDefinition} and a list of already reconstructed
 * {@link LinkedElectionBallot}s (anonymous vote content only) and produces a
 * {@link LinkedElectionResult}. It performs no persistence, no identity handling,
 * and no GUI/session work.
 *
 * <p><strong>Dependency-ordered counting.</strong> Contests are counted in the
 * deterministic topological order computed by {@link ElectionDependencyEvaluator}.
 * For an {@link OfficeDependencyType#EXCLUDE_WINNERS} rule, every winner of the
 * source office is removed from the dependent office's eligible candidates before
 * that office is counted — so the source office is necessarily counted first. If
 * the dependency graph contains a cycle (or references an unknown office) the
 * result is reported as incomplete and contests are counted in definition order
 * without applying exclusions, rather than pretending success.
 *
 * <p><strong>Determinism.</strong> Every ordering is resolved using the
 * definition's source candidate order, never randomness or unordered iteration.
 * IRV eliminates the tied-lowest candidate appearing latest in contest order (so
 * earlier-defined candidates survive).
 *
 * <p><strong>Approval cutoff ties are not broken by candidate order.</strong> For
 * APPROVAL_TOP_N, approval score is the only ranking that decides winners.
 * Candidates are walked in score-descending groups; a tied score group is elected
 * only if it fits entirely within the remaining seats. If a tied group is larger
 * than the seats left, none of its candidates is elected and the affected seats are
 * reported as unresolved (see {@link ContestResult#unresolvedSeatCount()} and
 * {@link ContestResult#unresolvedCandidateKeys()}), marking the contest and the
 * election incomplete. Candidate order is used only for display stability, never to
 * arbitrarily elect one tied candidate over another at the cutoff. A runoff or
 * administrator resolution fills unresolved seats outside this count.
 *
 * <p><strong>STV multi-seat counting.</strong> For {@code STV}, candidates are
 * ranked exactly as for IRV but multiple seats are filled by deterministic
 * fractional single-transferable-vote. A Droop quota
 * ({@code floor(validBallots / (seats + 1)) + 1}) is computed once from the
 * initial valid ballot value. Candidates reaching quota are elected and their
 * surplus is transferred at the Gregory fraction {@code surplus / tally}; when no
 * one reaches quota the lowest candidate is eliminated and transferred at full
 * value; ballots with no further continuing preference exhaust. All fractional
 * arithmetic uses a fixed scale and deterministic rounding so repeated counts of
 * the same ballots are identical. As with approval, candidate order is never used
 * to decide a seat-deciding tie. Two kinds of seat-deciding tie are left unresolved
 * (see {@link ContestResult#unresolvedSeatCount()}) for a runoff/admin resolution
 * rather than being broken by definition order: an <em>elimination</em> tie whose
 * outcome would change the final elected seats, and a <em>quota-election</em> tie in
 * which more candidates share the same top at-or-above-quota tally than there are
 * seats remaining. (Under a strict Droop quota the latter cannot arise — at most the
 * number of remaining seats can sit at or above quota at once — but the guard is kept
 * so candidate order can never decide a seat-winning quota tie.)
 *
 * <p>Counting is generic: no office or candidate name is hardcoded. "Mayor" and
 * "Council" are only illustrative configuration.
 */
public final class LinkedElectionCountingService {

    /** Fixed scale for all STV fractional arithmetic, for deterministic results. */
    private static final int STV_SCALE = 6;

    private final ElectionDependencyEvaluator dependencyEvaluator;

    public LinkedElectionCountingService() {
        this(new ElectionDependencyEvaluator());
    }

    public LinkedElectionCountingService(ElectionDependencyEvaluator dependencyEvaluator) {
        this.dependencyEvaluator = Objects.requireNonNull(dependencyEvaluator, "dependencyEvaluator");
    }

    /**
     * Counts every contest in the election and returns the combined result.
     *
     * @param poll       the poll being counted (used only for id/title; never for identity)
     * @param definition the validated election definition
     * @param ballots    the reconstructed anonymous ballots to count
     * @return the deterministic, anonymous election result
     */
    public LinkedElectionResult count(Poll poll,
                                      ElectionDefinition definition,
                                      List<LinkedElectionBallot> ballots) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(ballots, "ballots");

        List<String> electionIssues = new ArrayList<>();
        DependencyEvaluation evaluation = dependencyEvaluator.evaluateDependencies(definition);

        boolean dependenciesUsable = evaluation.allReferencesResolve() && !evaluation.hasCycle();
        boolean applyExclusions = dependenciesUsable;

        if (!evaluation.allReferencesResolve()) {
            electionIssues.add("Election definition references unknown offices in dependency rules: "
                    + String.join(", ", evaluation.unresolvedReferences())
                    + "; dependency exclusions were not applied.");
        }
        if (evaluation.hasCycle()) {
            electionIssues.add("Election dependency rules form a cycle; "
                    + "contests were counted in definition order without dependency exclusions.");
        }

        List<String> countingOrder = dependenciesUsable
                ? evaluation.evaluationOrder()
                : officeOrder(definition);

        // Winners per office, available to later (dependent) contests for exclusion.
        Map<String, List<String>> winnersByOffice = new LinkedHashMap<>();
        Map<String, ContestResult> resultsByOffice = new LinkedHashMap<>();

        for (String officeKey : countingOrder) {
            ContestDefinition contest = definition.findContest(officeKey).orElse(null);
            if (contest == null) {
                // Should not happen: countingOrder is derived from definition offices.
                continue;
            }

            Set<String> excluded = applyExclusions
                    ? excludedCandidatesFor(definition, officeKey, winnersByOffice)
                    : new LinkedHashSet<>();

            ContestResult contestResult = countContest(definition, contest, ballots, excluded);
            resultsByOffice.put(officeKey, contestResult);
            winnersByOffice.put(officeKey, contestResult.winners());
        }

        // Emit contests in counting order.
        List<ContestResult> orderedResults = new ArrayList<>();
        for (String officeKey : countingOrder) {
            ContestResult result = resultsByOffice.get(officeKey);
            if (result != null) {
                orderedResults.add(result);
            }
        }

        // The election is complete only if the dependency graph was usable AND no
        // contest was left with unresolved cutoff ties (a legitimate but incomplete
        // outcome requiring an external runoff/admin resolution).
        boolean allContestsComplete = orderedResults.stream().allMatch(ContestResult::complete);
        boolean complete = dependenciesUsable && allContestsComplete;

        return new LinkedElectionResult(
                poll.pollId(),
                poll.title(),
                complete,
                ballots.size(),
                0,
                orderedResults,
                electionIssues);
    }

    private List<String> officeOrder(ElectionDefinition definition) {
        List<String> order = new ArrayList<>();
        for (ContestDefinition contest : definition.contests()) {
            if (!order.contains(contest.officeKey())) {
                order.add(contest.officeKey());
            }
        }
        return order;
    }

    /**
     * Collects, in contest candidate order, the candidates that must be excluded
     * from {@code officeKey} because an EXCLUDE_WINNERS dependency names a source
     * office whose winners include them.
     */
    private Set<String> excludedCandidatesFor(ElectionDefinition definition,
                                              String officeKey,
                                              Map<String, List<String>> winnersByOffice) {
        Set<String> excludedWinners = new LinkedHashSet<>();
        for (OfficeDependencyRule rule : definition.dependencies()) {
            if (rule.type() != OfficeDependencyType.EXCLUDE_WINNERS) {
                continue;
            }
            if (!rule.appliesToOfficeKey().equals(officeKey)) {
                continue;
            }
            List<String> sourceWinners = winnersByOffice.get(rule.fromOfficeKey());
            if (sourceWinners != null) {
                excludedWinners.addAll(sourceWinners);
            }
        }
        return excludedWinners;
    }

    private ContestResult countContest(ElectionDefinition definition,
                                       ContestDefinition contest,
                                       List<LinkedElectionBallot> ballots,
                                       Set<String> excludedWinners) {
        String officeKey = contest.officeKey();

        // Structurally eligible candidates, in contest candidate order.
        List<String> structurallyEligible =
                dependencyEvaluator.determineCandidatesEligibleForContest(definition, officeKey);

        // Dependency-excluded candidates (intersection with eligible), in contest order.
        List<String> excluded = new ArrayList<>();
        List<String> eligible = new ArrayList<>();
        for (String candidateKey : structurallyEligible) {
            if (excludedWinners.contains(candidateKey)) {
                excluded.add(candidateKey);
            } else {
                eligible.add(candidateKey);
            }
        }

        if (contest.method() == CountingMethod.IRV) {
            return countIrv(contest, ballots, eligible, excluded);
        }
        if (contest.method() == CountingMethod.STV) {
            return countStv(contest, ballots, eligible, excluded);
        }
        return countApproval(contest, ballots, eligible, excluded);
    }

    // --- IRV ------------------------------------------------------------------

    private ContestResult countIrv(ContestDefinition contest,
                                   List<LinkedElectionBallot> ballots,
                                   List<String> eligible,
                                   List<String> excluded) {
        String officeKey = contest.officeKey();
        List<String> issues = new ArrayList<>();

        if (contest.seats() != 1) {
            issues.add("Office '" + officeKey + "' uses IRV but declares " + contest.seats()
                    + " seats; IRV is single-seat and only one winner was elected.");
        }

        // The ranked responses that participate in this office.
        List<List<String>> rankings = new ArrayList<>();
        for (LinkedElectionBallot ballot : ballots) {
            Optional<ContestVote> response = ballot.findResponse(officeKey);
            if (response.isPresent() && response.get() instanceof RankedContestVote ranked) {
                rankings.add(ranked.orderedCandidateKeys());
            }
        }

        List<IrvRoundResult> rounds = new ArrayList<>();
        Map<String, Integer> eliminationRound = new LinkedHashMap<>();

        // Continuing candidates, preserving contest order.
        LinkedHashSet<String> continuing = new LinkedHashSet<>(eligible);
        String winner = null;
        int finalExhausted = 0;

        if (eligible.isEmpty()) {
            issues.add("Office '" + officeKey + "' has no eligible candidates to elect.");
        }

        int roundNumber = 0;
        while (!continuing.isEmpty() && winner == null) {
            roundNumber++;

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String candidateKey : continuing) {
                counts.put(candidateKey, 0);
            }

            int counted = 0;
            int exhausted = 0;
            for (List<String> ranking : rankings) {
                String active = firstContinuing(ranking, continuing);
                if (active == null) {
                    exhausted++;
                } else {
                    counts.put(active, counts.get(active) + 1);
                    counted++;
                }
            }

            List<CandidateTally> tallies = orderedTallies(eligible, continuing, counts);
            List<String> continuingSnapshot = orderedContinuing(eligible, continuing);

            String majorityWinner = majorityWinner(continuing, counts, counted);
            boolean lastStanding = continuing.size() == 1;

            if (majorityWinner != null) {
                winner = majorityWinner;
                finalExhausted = exhausted;
                rounds.add(new IrvRoundResult(roundNumber, tallies, continuingSnapshot, null, winner, exhausted));
                break;
            }
            if (lastStanding) {
                winner = continuing.iterator().next();
                finalExhausted = exhausted;
                rounds.add(new IrvRoundResult(roundNumber, tallies, continuingSnapshot, null, winner, exhausted));
                break;
            }

            String eliminated = lowestTiedLatestInOrder(eligible, continuing, counts);
            rounds.add(new IrvRoundResult(roundNumber, tallies, continuingSnapshot, eliminated, null, exhausted));
            eliminationRound.put(eliminated, roundNumber);
            continuing.remove(eliminated);
        }

        // Final-round counts for candidate scores.
        Map<String, Integer> finalCounts = rounds.isEmpty()
                ? Map.of()
                : tallyMap(rounds.get(rounds.size() - 1).tallies());

        List<CandidateResult> candidateResults = new ArrayList<>();
        for (String candidateKey : eligible) {
            boolean elected = candidateKey.equals(winner);
            int score = finalCounts.getOrDefault(candidateKey, 0);
            candidateResults.add(new CandidateResult(
                    candidateKey, score, elected, false, eliminationRound.get(candidateKey)));
        }
        for (String candidateKey : excluded) {
            candidateResults.add(new CandidateResult(candidateKey, 0, false, true, null));
        }

        List<String> winners = winner == null ? List.of() : List.of(winner);

        return new ContestResult(
                officeKey,
                displayName(contest),
                CountingMethod.IRV,
                contest.seats(),
                winners,
                candidateResults,
                excluded,
                finalExhausted,
                rounds,
                issues);
    }

    private String firstContinuing(List<String> ranking, Set<String> continuing) {
        for (String candidateKey : ranking) {
            if (continuing.contains(candidateKey)) {
                return candidateKey;
            }
        }
        return null;
    }

    private String majorityWinner(Set<String> continuing, Map<String, Integer> counts, int counted) {
        // Strict majority of active (non-exhausted) ballots, evaluated in contest order.
        for (String candidateKey : continuing) {
            if (counts.get(candidateKey) * 2 > counted) {
                return candidateKey;
            }
        }
        return null;
    }

    /**
     * Returns the candidate to eliminate: the lowest tally, breaking ties by
     * eliminating the tied-lowest candidate that appears latest in contest order so
     * earlier-defined candidates survive. Fully deterministic.
     */
    private String lowestTiedLatestInOrder(List<String> contestOrder,
                                           Set<String> continuing,
                                           Map<String, Integer> counts) {
        int lowest = Integer.MAX_VALUE;
        for (String candidateKey : continuing) {
            lowest = Math.min(lowest, counts.get(candidateKey));
        }
        String eliminated = null;
        for (String candidateKey : contestOrder) {
            if (continuing.contains(candidateKey) && counts.get(candidateKey) == lowest) {
                // Keep overwriting so the latest in contest order wins the elimination.
                eliminated = candidateKey;
            }
        }
        return eliminated;
    }

    private List<CandidateTally> orderedTallies(List<String> contestOrder,
                                                Set<String> continuing,
                                                Map<String, Integer> counts) {
        List<CandidateTally> tallies = new ArrayList<>();
        for (String candidateKey : contestOrder) {
            if (continuing.contains(candidateKey)) {
                tallies.add(new CandidateTally(candidateKey, counts.getOrDefault(candidateKey, 0)));
            }
        }
        return tallies;
    }

    private List<String> orderedContinuing(List<String> contestOrder, Set<String> continuing) {
        List<String> out = new ArrayList<>();
        for (String candidateKey : contestOrder) {
            if (continuing.contains(candidateKey)) {
                out.add(candidateKey);
            }
        }
        return out;
    }

    private Map<String, Integer> tallyMap(List<CandidateTally> tallies) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (CandidateTally tally : tallies) {
            map.put(tally.candidateKey(), tally.votes());
        }
        return map;
    }

    // --- STV ------------------------------------------------------------------

    /**
     * One ballot participating in an STV count: its ranked preferences restricted
     * to candidates eligible for the contest, a cursor to the candidate it is
     * currently allocated to, and its current fractional value.
     */
    private static final class StvBallot {
        final List<String> prefs;
        int cursor;
        BigDecimal value;

        StvBallot(List<String> prefs, BigDecimal value) {
            this.prefs = prefs;
            this.cursor = 0;
            this.value = value;
        }
    }

    private ContestResult countStv(ContestDefinition contest,
                                   List<LinkedElectionBallot> ballots,
                                   List<String> eligible,
                                   List<String> excluded) {
        String officeKey = contest.officeKey();
        int seats = contest.seats();
        List<String> issues = new ArrayList<>();

        Set<String> eligibleSet = new LinkedHashSet<>(eligible);

        // Build per-ballot preference lists, restricted to eligible candidates in
        // voter order. A ballot with no eligible preference exhausts immediately.
        List<StvBallot> stvBallots = new ArrayList<>();
        BigDecimal exhausted = BigDecimal.ZERO.setScale(STV_SCALE, RoundingMode.UNNECESSARY);
        int validBallots = 0;
        for (LinkedElectionBallot ballot : ballots) {
            Optional<ContestVote> response = ballot.findResponse(officeKey);
            if (response.isEmpty() || !(response.get() instanceof RankedContestVote ranked)) {
                continue;
            }
            List<String> prefs = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String candidateKey : ranked.orderedCandidateKeys()) {
                if (eligibleSet.contains(candidateKey) && seen.add(candidateKey)) {
                    prefs.add(candidateKey);
                }
            }
            if (prefs.isEmpty()) {
                exhausted = exhausted.add(BigDecimal.ONE);
                continue;
            }
            validBallots++;
            stvBallots.add(new StvBallot(prefs, BigDecimal.ONE.setScale(STV_SCALE, RoundingMode.UNNECESSARY)));
        }

        // Droop quota from the initial valid ballot value (computed once).
        BigDecimal quota = BigDecimal.valueOf((validBallots / (seats + 1)) + 1L)
                .setScale(STV_SCALE, RoundingMode.UNNECESSARY);

        if (eligible.isEmpty()) {
            issues.add("Office '" + officeKey + "' has no eligible candidates to elect.");
        }
        if (!eligible.isEmpty() && eligible.size() < seats) {
            issues.add("Office '" + officeKey + "' has " + eligible.size()
                    + " eligible candidate(s) for " + seats
                    + " seat(s); only " + eligible.size() + " seat(s) could be filled.");
        }

        // Continuing candidates in contest order; elected in election order.
        LinkedHashSet<String> continuing = new LinkedHashSet<>(eligible);
        List<String> elected = new ArrayList<>();
        Set<String> eliminated = new LinkedHashSet<>();
        Map<String, BigDecimal> finalValue = new LinkedHashMap<>();

        List<StvRoundResult> rounds = new ArrayList<>();
        int unresolvedSeatCount = 0;
        List<String> unresolvedCandidateKeys = List.of();
        boolean complete = true;

        int roundNumber = 0;
        while (elected.size() < seats && !continuing.isEmpty()) {
            roundNumber++;
            int remainingSeats = seats - elected.size();

            // Current pile totals for every continuing candidate, in contest order.
            Map<String, BigDecimal> tally = stvTally(eligible, continuing, elected, stvBallots);
            List<StvCandidateTally> snapshot = stvSnapshot(eligible, continuing, tally);

            // If the continuing candidates can all be elected, fill the remaining seats.
            if (continuing.size() <= remainingSeats) {
                List<String> rest = orderedContinuing(eligible, continuing);
                elected.addAll(rest);
                for (String candidateKey : rest) {
                    finalValue.put(candidateKey, tally.get(candidateKey));
                }
                continuing.clear();
                rounds.add(new StvRoundResult(roundNumber, snapshot, List.copyOf(rest), null,
                        "Remaining candidates fill the last seat(s): " + String.join(", ", rest) + "."));
                break;
            }

            // Candidates at or above quota are elected, but candidate order must never
            // decide a seat-winning quota tie. Take the full group of continuing
            // candidates tied at the highest at-or-above-quota tally. If that tied
            // group is larger than the seats remaining, none of them can be separated
            // by the count, so leave those seats unresolved for a runoff/admin
            // resolution instead of electing by candidate order.
            List<String> quotaGroup = highestAtOrAboveQuotaGroup(eligible, continuing, tally, quota);
            if (!quotaGroup.isEmpty()) {
                if (quotaGroup.size() > remainingSeats) {
                    unresolvedSeatCount = remainingSeats;
                    unresolvedCandidateKeys = List.copyOf(quotaGroup);
                    complete = false;
                    issues.add("Office '" + officeKey + "' has an STV quota tie that decides the final seat(s): "
                            + unresolvedSeatCount + " seat(s) remain unresolved among tied candidates ["
                            + String.join(", ", unresolvedCandidateKeys)
                            + "] sharing the same at-or-above-quota tally. A runoff or administrator "
                            + "resolution is required; no tied candidate was elected by candidate order.");
                    rounds.add(new StvRoundResult(roundNumber, snapshot, List.of(), null,
                            "Quota-election tie decides the final seat(s); "
                                    + unresolvedSeatCount + " seat(s) left unresolved among ["
                                    + String.join(", ", unresolvedCandidateKeys) + "]."));
                    break;
                }

                // The tied quota group fits within the remaining seats, so every member
                // will be elected. Process the highest member (group is already in
                // contest order) now; candidate order only sequences processing here, it
                // never chooses winners, because the rest of the tied group is elected in
                // subsequent rounds. Elect by the Gregory method: the candidate retains
                // exactly quota and every ballot leaves at the surplus fraction. A zero
                // surplus transfers ballots at value 0, which both contributes nothing
                // and prevents any later re-count.
                String quotaWinner = quotaGroup.get(0);
                BigDecimal candidateTotal = tally.get(quotaWinner);
                BigDecimal surplus = candidateTotal.subtract(quota);
                elected.add(quotaWinner);
                finalValue.put(quotaWinner, quota);
                continuing.remove(quotaWinner);

                BigDecimal transferValue = surplus.signum() > 0
                        ? surplus.divide(candidateTotal, STV_SCALE, RoundingMode.DOWN)
                        : zero();
                exhausted = exhausted.add(transferSurplus(quotaWinner, transferValue, continuing, elected, stvBallots));
                String summary = surplus.signum() > 0
                        ? quotaWinner + " reached quota (" + plain(candidateTotal) + " ≥ "
                                + plain(quota) + ") and is elected; surplus " + plain(surplus)
                                + " transferred at value " + plain(transferValue) + "."
                        : quotaWinner + " reached quota exactly (" + plain(quota)
                                + ") and is elected; no surplus to transfer.";
                rounds.add(new StvRoundResult(roundNumber, snapshot, List.of(quotaWinner), null, summary));
                continue;
            }

            // No one reached quota: eliminate the lowest, unless the elimination would
            // decide the final seat(s) among tied-lowest candidates.
            List<String> lowestTie = lowestTiedGroup(eligible, continuing, tally);
            if (lowestTie.size() >= 2 && continuing.size() - lowestTie.size() < remainingSeats) {
                // Removing the whole tied group would drop below the remaining seats, so
                // at least one tied candidate must be elected — and they are
                // indistinguishable by the count. Leave the seats unresolved.
                unresolvedSeatCount = remainingSeats;
                unresolvedCandidateKeys = List.copyOf(lowestTie);
                complete = false;
                issues.add("Office '" + officeKey + "' has an STV elimination tie that decides the final seat(s): "
                        + unresolvedSeatCount + " seat(s) remain unresolved among tied candidates ["
                        + String.join(", ", unresolvedCandidateKeys)
                        + "]. A runoff or administrator resolution is required; "
                        + "no tied candidate was eliminated or elected by candidate order.");
                rounds.add(new StvRoundResult(roundNumber, snapshot, List.of(), null,
                        "Elimination tie decides the final seat(s); "
                                + unresolvedSeatCount + " seat(s) left unresolved among ["
                                + String.join(", ", unresolvedCandidateKeys) + "]."));
                break;
            }

            // Safe elimination: break a non-deciding tie deterministically by latest in
            // contest order (earlier-defined candidates survive), matching IRV.
            String toEliminate = lowestTie.get(lowestTie.size() - 1);
            continuing.remove(toEliminate);
            eliminated.add(toEliminate);
            finalValue.put(toEliminate, tally.get(toEliminate));
            exhausted = exhausted.add(transferEliminated(toEliminate, continuing, elected, stvBallots));
            String tieNote = lowestTie.size() >= 2
                    ? " (lowest tie broken by latest contest order among [" + String.join(", ", lowestTie) + "])"
                    : "";
            rounds.add(new StvRoundResult(roundNumber, snapshot, List.of(), toEliminate,
                    toEliminate + " has the lowest tally (" + plain(tally.get(toEliminate))
                            + ") and is eliminated" + tieNote + "; ballots transferred to next preference."));
        }

        // Record final values for any candidate not yet captured (still continuing at a
        // break, or unresolved). Recompute a final tally pass for completeness.
        Map<String, BigDecimal> finalTally = stvTally(eligible, continuing, elected, stvBallots);
        for (String candidateKey : eligible) {
            finalValue.putIfAbsent(candidateKey,
                    finalTally.getOrDefault(candidateKey, zero()));
        }

        Set<String> winnerSet = new LinkedHashSet<>(elected);
        Set<String> unresolvedSet = new LinkedHashSet<>(unresolvedCandidateKeys);
        List<CandidateResult> candidateResults = new ArrayList<>();
        for (String candidateKey : eligible) {
            BigDecimal value = finalValue.getOrDefault(candidateKey, zero());
            int score = value.setScale(0, RoundingMode.DOWN).intValue();
            Integer eliminationRound = null; // STV rounds are reported via StvResultData, not per-candidate.
            candidateResults.add(new CandidateResult(
                    candidateKey, score, winnerSet.contains(candidateKey), false, eliminationRound,
                    unresolvedSet.contains(candidateKey)));
        }
        for (String candidateKey : excluded) {
            candidateResults.add(new CandidateResult(candidateKey, 0, false, true, null));
        }

        List<StvCandidateTally> finalTallies = new ArrayList<>();
        for (String candidateKey : eligible) {
            finalTallies.add(new StvCandidateTally(candidateKey, plain(finalValue.getOrDefault(candidateKey, zero()))));
        }

        StvResultData stv = new StvResultData(plain(quota), plain(exhausted), finalTallies, rounds);

        return new ContestResult(
                officeKey,
                displayName(contest),
                CountingMethod.STV,
                seats,
                List.copyOf(elected),
                candidateResults,
                excluded,
                0,
                List.of(),
                issues,
                complete,
                unresolvedSeatCount,
                unresolvedCandidateKeys,
                stv);
    }

    /** Sum each continuing candidate's currently-allocated ballot value. */
    private Map<String, BigDecimal> stvTally(List<String> contestOrder,
                                             Set<String> continuing,
                                             List<String> elected,
                                             List<StvBallot> ballots) {
        Set<String> active = new LinkedHashSet<>(continuing);
        Map<String, BigDecimal> tally = new LinkedHashMap<>();
        for (String candidateKey : contestOrder) {
            if (active.contains(candidateKey)) {
                tally.put(candidateKey, zero());
            }
        }
        for (StvBallot ballot : ballots) {
            String holder = currentHolder(ballot, active);
            if (holder != null) {
                tally.put(holder, tally.get(holder).add(ballot.value));
            }
        }
        return tally;
    }

    /**
     * Advances a ballot's cursor to its highest-ranked candidate that is currently
     * "active" (a continuing candidate that can still receive value), and returns
     * that candidate, or {@code null} if the ballot has exhausted.
     */
    private String currentHolder(StvBallot ballot, Set<String> active) {
        while (ballot.cursor < ballot.prefs.size() && !active.contains(ballot.prefs.get(ballot.cursor))) {
            ballot.cursor++;
        }
        return ballot.cursor < ballot.prefs.size() ? ballot.prefs.get(ballot.cursor) : null;
    }

    private List<StvCandidateTally> stvSnapshot(List<String> contestOrder,
                                                Set<String> continuing,
                                                Map<String, BigDecimal> tally) {
        List<StvCandidateTally> snapshot = new ArrayList<>();
        for (String candidateKey : contestOrder) {
            if (continuing.contains(candidateKey)) {
                snapshot.add(new StvCandidateTally(candidateKey, plain(tally.getOrDefault(candidateKey, zero()))));
            }
        }
        return snapshot;
    }

    /**
     * Returns every continuing candidate sharing the highest tally among those at or
     * above quota, in contest order; empty if none reached quota.
     *
     * <p>The full tied group — not a single order-picked candidate — is what the
     * count uses to decide quota elections. When the group fits within the remaining
     * seats every member is elected (contest order only sequences processing), so
     * candidate order never chooses a winner. When the group is larger than the seats
     * remaining the seats are left unresolved rather than broken by order. Returning
     * the complete tied group is therefore the guarantee that no seat-winning quota
     * tie is silently resolved by candidate definition order.
     *
     * <p>Package-private so the fairness-critical grouping can be unit-tested
     * directly. (A strict Droop quota makes a tied group larger than the remaining
     * seats unreachable through real ballots, so this is exercised at the unit level.)
     */
    List<String> highestAtOrAboveQuotaGroup(List<String> contestOrder,
                                            Set<String> continuing,
                                            Map<String, BigDecimal> tally,
                                            BigDecimal quota) {
        BigDecimal highest = null;
        for (String candidateKey : contestOrder) {
            if (!continuing.contains(candidateKey)) {
                continue;
            }
            BigDecimal value = tally.get(candidateKey);
            if (value.compareTo(quota) >= 0 && (highest == null || value.compareTo(highest) > 0)) {
                highest = value;
            }
        }
        List<String> group = new ArrayList<>();
        if (highest == null) {
            return group;
        }
        for (String candidateKey : contestOrder) {
            if (continuing.contains(candidateKey) && tally.get(candidateKey).compareTo(highest) == 0) {
                group.add(candidateKey);
            }
        }
        return group;
    }

    /** The continuing candidates sharing the lowest tally, in contest order. */
    private List<String> lowestTiedGroup(List<String> contestOrder,
                                         Set<String> continuing,
                                         Map<String, BigDecimal> tally) {
        BigDecimal lowest = null;
        for (String candidateKey : contestOrder) {
            if (!continuing.contains(candidateKey)) {
                continue;
            }
            BigDecimal value = tally.get(candidateKey);
            if (lowest == null || value.compareTo(lowest) < 0) {
                lowest = value;
            }
        }
        List<String> group = new ArrayList<>();
        for (String candidateKey : contestOrder) {
            if (continuing.contains(candidateKey) && tally.get(candidateKey).compareTo(lowest) == 0) {
                group.add(candidateKey);
            }
        }
        return group;
    }

    /**
     * Transfers an elected candidate's surplus: every ballot allocated to them
     * leaves at {@code value * transferValue} to its next continuing preference.
     *
     * @return the value that exhausted (had no continuing preference to receive it)
     */
    private BigDecimal transferSurplus(String elected,
                                       BigDecimal transferValue,
                                       Set<String> continuing,
                                       List<String> electedList,
                                       List<StvBallot> ballots) {
        Set<String> active = new LinkedHashSet<>(continuing);
        active.add(elected); // so currentHolder can still find ballots sitting with the just-elected candidate
        BigDecimal exhausted = zero();
        for (StvBallot ballot : ballots) {
            if (!elected.equals(currentHolder(ballot, active))) {
                continue;
            }
            ballot.value = ballot.value.multiply(transferValue).setScale(STV_SCALE, RoundingMode.DOWN);
            ballot.cursor++; // leave the elected candidate
            String next = currentHolder(ballot, continuing);
            if (next == null) {
                exhausted = exhausted.add(ballot.value);
                ballot.value = zero();
            }
        }
        return exhausted;
    }

    /**
     * Transfers an eliminated candidate's ballots at full value to their next
     * continuing preference.
     *
     * @return the value that exhausted (had no continuing preference to receive it)
     */
    private BigDecimal transferEliminated(String eliminated,
                                          Set<String> continuing,
                                          List<String> electedList,
                                          List<StvBallot> ballots) {
        Set<String> active = new LinkedHashSet<>(continuing);
        active.add(eliminated);
        BigDecimal exhausted = zero();
        for (StvBallot ballot : ballots) {
            if (!eliminated.equals(currentHolder(ballot, active))) {
                continue;
            }
            ballot.cursor++; // leave the eliminated candidate
            String next = currentHolder(ballot, continuing);
            if (next == null) {
                exhausted = exhausted.add(ballot.value);
                ballot.value = zero();
            }
        }
        return exhausted;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(STV_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String plain(BigDecimal value) {
        return value.setScale(STV_SCALE, RoundingMode.DOWN).toPlainString();
    }

    // --- Approval -------------------------------------------------------------

    private ContestResult countApproval(ContestDefinition contest,
                                        List<LinkedElectionBallot> ballots,
                                        List<String> eligible,
                                        List<String> excluded) {
        String officeKey = contest.officeKey();
        List<String> issues = new ArrayList<>();

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String candidateKey : eligible) {
            scores.put(candidateKey, 0);
        }

        for (LinkedElectionBallot ballot : ballots) {
            Optional<ContestVote> response = ballot.findResponse(officeKey);
            if (response.isEmpty() || !(response.get() instanceof ApprovalContestVote approval)) {
                continue;
            }
            // Count each distinct eligible approval once; ignore excluded/ineligible selections.
            Set<String> counted = new LinkedHashSet<>();
            for (String candidateKey : approval.selectedCandidateKeys()) {
                if (scores.containsKey(candidateKey) && counted.add(candidateKey)) {
                    scores.put(candidateKey, scores.get(candidateKey) + 1);
                }
            }
        }

        // Rank by score descending, then contest order ascending (index in eligible).
        Map<String, Integer> orderIndex = new LinkedHashMap<>();
        for (int i = 0; i < eligible.size(); i++) {
            orderIndex.put(eligible.get(i), i);
        }
        List<String> ranked = new ArrayList<>(eligible);
        ranked.sort((a, b) -> {
            int byScore = Integer.compare(scores.get(b), scores.get(a));
            if (byScore != 0) {
                return byScore;
            }
            return Integer.compare(orderIndex.get(a), orderIndex.get(b));
        });

        int seats = contest.seats();

        // Walk score groups from highest to lowest, electing a whole tied group only
        // when it fits within the remaining seats. Candidate order provides display
        // stability (the sort above) but never decides winners at the cutoff: if a
        // tied group is larger than the seats left, no candidate in it is elected and
        // those seats are reported unresolved for an external runoff/admin resolution.
        List<String> winners = new ArrayList<>();
        int unresolvedSeatCount = 0;
        List<String> unresolvedCandidateKeys = List.of();
        boolean complete = true;

        int remaining = seats;
        int i = 0;
        while (i < ranked.size() && remaining > 0) {
            int groupScore = scores.get(ranked.get(i));
            List<String> group = new ArrayList<>();
            int j = i;
            while (j < ranked.size() && scores.get(ranked.get(j)) == groupScore) {
                group.add(ranked.get(j));
                j++;
            }

            if (group.size() <= remaining) {
                winners.addAll(group);
                remaining -= group.size();
                i = j;
            } else {
                // Tie crosses the seat cutoff: the group cannot be separated by counting.
                unresolvedSeatCount = remaining;
                unresolvedCandidateKeys = List.copyOf(group);
                complete = false;
                issues.add("Office '" + officeKey + "' has an approval tie at the seat cutoff: "
                        + unresolvedSeatCount + " seat(s) remain unresolved among tied candidates ["
                        + String.join(", ", unresolvedCandidateKeys)
                        + "]. A runoff or administrator resolution is required; "
                        + "no tied candidate was elected by candidate order.");
                break;
            }
        }

        if (eligible.size() < seats && unresolvedSeatCount == 0) {
            issues.add("Office '" + officeKey + "' has " + eligible.size()
                    + " eligible candidate(s) for " + seats + " seat(s); only "
                    + winners.size() + " seat(s) could be filled.");
        }

        Set<String> winnerSet = new LinkedHashSet<>(winners);
        Set<String> unresolvedSet = new LinkedHashSet<>(unresolvedCandidateKeys);
        List<CandidateResult> candidateResults = new ArrayList<>();
        for (String candidateKey : eligible) {
            candidateResults.add(new CandidateResult(
                    candidateKey, scores.get(candidateKey), winnerSet.contains(candidateKey), false, null,
                    unresolvedSet.contains(candidateKey)));
        }
        for (String candidateKey : excluded) {
            candidateResults.add(new CandidateResult(candidateKey, 0, false, true, null));
        }

        return new ContestResult(
                officeKey,
                displayName(contest),
                CountingMethod.APPROVAL_TOP_N,
                seats,
                winners,
                candidateResults,
                excluded,
                0,
                List.of(),
                issues,
                complete,
                unresolvedSeatCount,
                unresolvedCandidateKeys);
    }

    private String displayName(ContestDefinition contest) {
        return contest.displayName() == null || contest.displayName().isBlank()
                ? contest.officeKey()
                : contest.displayName();
    }
}
