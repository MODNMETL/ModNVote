package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Interprets an {@link ElectionDefinition}'s generic dependency rules.
 *
 * <p>This evaluator deliberately does <strong>not</strong> apply any contest
 * outcomes. No winners are computed and no candidates are removed on the basis
 * of results. Its sole purpose in this tranche is to formalize, deterministically,
 * how dependencies constrain candidate eligibility and counting order, so later
 * tranches have one unambiguous interpretation to build counting on.
 *
 * <p>All output is deterministic: ordering is derived from the definition's
 * source order (office order, candidate order), never from hashing or iteration
 * of unordered collections.
 */
public final class ElectionDependencyEvaluator {

    /**
     * Returns the candidate keys that are eligible to receive votes for the given
     * office, in the contest's defined candidate order.
     *
     * <p>Eligibility here is purely structural: a candidate is eligible if it is
     * listed for the office and its definition declares the office in its
     * eligible offices. Dependency <em>outcomes</em> (e.g. excluding winners of
     * another contest) are intentionally not applied — that happens at count time
     * in a later tranche.
     *
     * @param definition the election definition
     * @param officeKey  the office to determine eligibility for
     * @return eligible candidate keys in contest order, or an empty list if the office is unknown
     */
    public List<String> determineCandidatesEligibleForContest(ElectionDefinition definition, String officeKey) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(officeKey, "officeKey");

        ContestDefinition contest = definition.findContest(officeKey).orElse(null);
        if (contest == null) {
            return List.of();
        }

        List<String> eligible = new ArrayList<>();
        for (String candidateKey : contest.candidateKeys()) {
            CandidateDefinition candidate = definition.findCandidate(candidateKey).orElse(null);
            if (candidate != null && candidate.eligibleOfficeKeys().contains(officeKey)) {
                eligible.add(candidateKey);
            }
        }
        return List.copyOf(eligible);
    }

    /**
     * Interprets all dependency rules into a deterministic {@link DependencyEvaluation}.
     *
     * <p>For each {@link OfficeDependencyRule}, the source office must be counted
     * before the office the rule applies to. The evaluation captures unresolved
     * references, the per-office set of preceding offices, a deterministic
     * topological counting order, and whether the graph contains a cycle. No
     * outcomes are computed.
     */
    public DependencyEvaluation evaluateDependencies(ElectionDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        // Offices in definition source order — the deterministic tie-breaker for all ordering.
        List<String> officeOrder = new ArrayList<>();
        Set<String> officeKeys = new LinkedHashSet<>();
        for (ContestDefinition contest : definition.contests()) {
            if (officeKeys.add(contest.officeKey())) {
                officeOrder.add(contest.officeKey());
            }
        }

        List<String> unresolved = new ArrayList<>();
        // appliesTo -> ordered set of preceding (must-count-first) offices.
        Map<String, LinkedHashSet<String>> preceding = new LinkedHashMap<>();
        for (String office : officeOrder) {
            preceding.put(office, new LinkedHashSet<>());
        }

        for (OfficeDependencyRule rule : definition.dependencies()) {
            boolean fromKnown = officeKeys.contains(rule.fromOfficeKey());
            boolean appliesKnown = officeKeys.contains(rule.appliesToOfficeKey());
            if (!fromKnown) {
                unresolved.add(rule.fromOfficeKey());
            }
            if (!appliesKnown) {
                unresolved.add(rule.appliesToOfficeKey());
            }
            if (fromKnown && appliesKnown) {
                preceding.get(rule.appliesToOfficeKey()).add(rule.fromOfficeKey());
            }
        }

        Map<String, List<String>> precedingByOffice = new LinkedHashMap<>();
        for (String office : officeOrder) {
            precedingByOffice.put(office, List.copyOf(preceding.get(office)));
        }

        TopoResult topo = topologicalOrder(officeOrder, precedingByOffice);

        return new DependencyEvaluation(
                unresolved.isEmpty(),
                List.copyOf(unresolved),
                precedingByOffice,
                topo.order(),
                topo.hasCycle());
    }

    private TopoResult topologicalOrder(List<String> officeOrder,
                                        Map<String, List<String>> precedingByOffice) {
        // Deterministic Kahn-style sort: repeatedly emit, in source order, any office
        // whose preceding offices have all been emitted.
        List<String> order = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();

        boolean progress = true;
        while (order.size() < officeOrder.size() && progress) {
            progress = false;
            for (String office : officeOrder) {
                if (emitted.contains(office)) {
                    continue;
                }
                if (emitted.containsAll(precedingByOffice.get(office))) {
                    order.add(office);
                    emitted.add(office);
                    progress = true;
                }
            }
        }

        boolean cycle = order.size() < officeOrder.size();
        return new TopoResult(cycle ? List.of() : List.copyOf(order), cycle);
    }

    private record TopoResult(List<String> order, boolean hasCycle) {
    }
}
