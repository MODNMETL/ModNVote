package com.modnmetl.modnvote.domain.election.execution;

import java.util.List;
import java.util.Map;

/**
 * The deterministic result of interpreting an election's dependency rules,
 * without applying any contest outcomes.
 *
 * This formalizes how dependencies constrain ordering so future counting can
 * rely on a single, unambiguous interpretation.
 *
 * @param allReferencesResolve  true if every dependency endpoint is a known office
 * @param unresolvedReferences  office keys referenced by dependencies that do not exist, in encounter order
 * @param precedingOfficesByOffice for each office, the offices whose results it depends on
 *                                 (must be counted first), in deterministic order
 * @param evaluationOrder       a deterministic topological order in which contests would be
 *                              counted (dependencies before dependents); empty if a cycle exists
 * @param hasCycle              true if the dependency graph contains a cycle
 */
public record DependencyEvaluation(
        boolean allReferencesResolve,
        List<String> unresolvedReferences,
        Map<String, List<String>> precedingOfficesByOffice,
        List<String> evaluationOrder,
        boolean hasCycle
) {
    public DependencyEvaluation {
        unresolvedReferences = unresolvedReferences == null ? List.of() : List.copyOf(unresolvedReferences);
        precedingOfficesByOffice = precedingOfficesByOffice == null ? Map.of() : Map.copyOf(precedingOfficesByOffice);
        evaluationOrder = evaluationOrder == null ? List.of() : List.copyOf(evaluationOrder);
    }
}
