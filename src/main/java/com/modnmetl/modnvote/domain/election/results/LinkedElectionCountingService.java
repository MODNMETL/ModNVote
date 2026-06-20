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
 * <p><strong>Determinism.</strong> Every tie-break is resolved using the
 * definition's source candidate order, never randomness or unordered iteration.
 * IRV eliminates the tied-lowest candidate appearing latest in contest order (so
 * earlier-defined candidates survive); approval ranks by score descending then
 * contest order ascending.
 *
 * <p>Counting is generic: no office or candidate name is hardcoded. "Mayor" and
 * "Council" are only illustrative configuration.
 */
public final class LinkedElectionCountingService {

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

        boolean complete = dependenciesUsable;

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
        List<String> winners = new ArrayList<>();
        for (String candidateKey : ranked) {
            if (winners.size() >= seats) {
                break;
            }
            winners.add(candidateKey);
        }

        if (eligible.size() < seats) {
            issues.add("Office '" + officeKey + "' has " + eligible.size()
                    + " eligible candidate(s) for " + seats + " seat(s); only "
                    + winners.size() + " seat(s) could be filled.");
        }

        Set<String> winnerSet = new LinkedHashSet<>(winners);
        List<CandidateResult> candidateResults = new ArrayList<>();
        for (String candidateKey : eligible) {
            candidateResults.add(new CandidateResult(
                    candidateKey, scores.get(candidateKey), winnerSet.contains(candidateKey), false, null));
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
                issues);
    }

    private String displayName(ContestDefinition contest) {
        return contest.displayName() == null || contest.displayName().isBlank()
                ? contest.officeKey()
                : contest.displayName();
    }
}
