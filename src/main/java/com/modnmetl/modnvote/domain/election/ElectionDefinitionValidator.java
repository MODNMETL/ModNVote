package com.modnmetl.modnvote.domain.election;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates a generic {@link ElectionDefinition}.
 *
 * Validation is fully generic: no office name (such as Mayor or Council) is
 * hardcoded. All rules are expressed in terms of generic offices, candidates,
 * and dependencies.
 *
 * This validator does not implement voting, persistence, counting, or GUI flow.
 * It exists so future tranches can reject malformed definitions before any
 * election is opened.
 */
public final class ElectionDefinitionValidator {

    /**
     * Validates the definition, throwing {@link ElectionDefinitionException} with
     * a combined, admin-facing message if any rule is violated.
     */
    public void validate(ElectionDefinition definition) {
        List<String> issues = findIssues(definition);
        if (!issues.isEmpty()) {
            throw new ElectionDefinitionException(
                    "Invalid linked offices election definition: " + String.join("; ", issues));
        }
    }

    /**
     * Returns all validation issues for the definition, or an empty list if valid.
     */
    public List<String> findIssues(ElectionDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        List<String> issues = new ArrayList<>();

        if (!ElectionDefinition.LINKED_OFFICES_MODEL.equals(definition.model())) {
            issues.add("model must be '" + ElectionDefinition.LINKED_OFFICES_MODEL + "'.");
        }

        if (definition.contests().isEmpty()) {
            issues.add("at least one contest is required.");
        }

        Set<String> officeKeys = new LinkedHashSet<>();
        Set<String> duplicateOffices = new LinkedHashSet<>();
        for (ContestDefinition contest : definition.contests()) {
            if (!officeKeys.add(contest.officeKey())) {
                duplicateOffices.add(contest.officeKey());
            }
        }
        for (String duplicate : duplicateOffices) {
            issues.add("duplicate office key '" + duplicate + "'.");
        }

        Map<String, CandidateDefinition> candidatesByKey = new HashMap<>();
        Set<String> duplicateCandidates = new LinkedHashSet<>();
        for (CandidateDefinition candidate : definition.candidates()) {
            if (candidatesByKey.put(candidate.candidateKey(), candidate) != null) {
                duplicateCandidates.add(candidate.candidateKey());
            }
        }
        for (String duplicate : duplicateCandidates) {
            issues.add("duplicate candidate key '" + duplicate + "'.");
        }

        for (ContestDefinition contest : definition.contests()) {
            validateContest(contest, candidatesByKey, issues);
        }

        for (CandidateDefinition candidate : definition.candidates()) {
            if (isBlank(candidate.displayName())) {
                issues.add("candidate '" + candidate.candidateKey() + "' has a blank display name.");
            }
            for (String officeKey : candidate.eligibleOfficeKeys()) {
                if (!officeKeys.contains(officeKey)) {
                    issues.add("candidate '" + candidate.candidateKey()
                            + "' is eligible for unknown office '" + officeKey + "'.");
                }
            }
        }

        validateDependencies(definition, officeKeys, issues);

        return issues;
    }

    private void validateContest(ContestDefinition contest,
                                 Map<String, CandidateDefinition> candidatesByKey,
                                 List<String> issues) {
        String officeKey = contest.officeKey();

        if (isBlank(contest.displayName())) {
            issues.add("office '" + officeKey + "' has a blank display name.");
        }

        if (contest.method() == null) {
            issues.add("office '" + officeKey + "' is missing a counting method.");
        }

        if (contest.seats() < 1) {
            issues.add("office '" + officeKey + "' must have seats >= 1.");
        }

        if (contest.method() == CountingMethod.IRV && contest.seats() != 1) {
            issues.add("office '" + officeKey + "' uses IRV and must have exactly 1 seat.");
        }

        if (contest.method() == CountingMethod.APPROVAL_TOP_N) {
            Integer maxSelections = contest.maxSelections();
            if (maxSelections == null || maxSelections < 1) {
                issues.add("office '" + officeKey + "' uses APPROVAL_TOP_N and must have maxSelections >= 1.");
            } else if (maxSelections < contest.seats()) {
                issues.add("office '" + officeKey + "' uses APPROVAL_TOP_N and must have maxSelections >= seats.");
            }
        }

        int eligibleCount = 0;
        for (String candidateKey : contest.candidateKeys()) {
            CandidateDefinition candidate = candidatesByKey.get(candidateKey);
            if (candidate == null) {
                issues.add("office '" + officeKey + "' references unknown candidate '" + candidateKey + "'.");
                continue;
            }
            if (!candidate.eligibleOfficeKeys().contains(officeKey)) {
                issues.add("candidate '" + candidateKey + "' is listed for office '" + officeKey
                        + "' but is not eligible for it.");
            } else {
                eligibleCount++;
            }
        }

        if (contest.seats() >= 1 && eligibleCount < contest.seats()) {
            issues.add("office '" + officeKey + "' has fewer eligible candidates (" + eligibleCount
                    + ") than seats (" + contest.seats() + ").");
        }
    }

    private void validateDependencies(ElectionDefinition definition,
                                      Set<String> officeKeys,
                                      List<String> issues) {
        Map<String, List<String>> adjacency = new HashMap<>();
        boolean allEndpointsValid = true;

        for (OfficeDependencyRule rule : definition.dependencies()) {
            boolean validEndpoints = true;
            if (!officeKeys.contains(rule.fromOfficeKey())) {
                issues.add("dependency references unknown office '" + rule.fromOfficeKey() + "'.");
                validEndpoints = false;
            }
            if (!officeKeys.contains(rule.appliesToOfficeKey())) {
                issues.add("dependency references unknown office '" + rule.appliesToOfficeKey() + "'.");
                validEndpoints = false;
            }
            if (!validEndpoints) {
                allEndpointsValid = false;
                continue;
            }
            // Source contest must be counted before the dependent contest.
            adjacency.computeIfAbsent(rule.fromOfficeKey(), k -> new ArrayList<>())
                    .add(rule.appliesToOfficeKey());
        }

        if (allEndpointsValid && hasCycle(officeKeys, adjacency)) {
            issues.add("dependency graph contains a cycle.");
        }
    }

    private boolean hasCycle(Set<String> nodes, Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (String node : nodes) {
            if (dfsHasCycle(node, adjacency, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfsHasCycle(String node,
                                Map<String, List<String>> adjacency,
                                Set<String> visited,
                                Set<String> inStack) {
        if (inStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        inStack.add(node);
        for (String next : adjacency.getOrDefault(node, List.of())) {
            if (dfsHasCycle(next, adjacency, visited, inStack)) {
                return true;
            }
        }
        inStack.remove(node);
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
