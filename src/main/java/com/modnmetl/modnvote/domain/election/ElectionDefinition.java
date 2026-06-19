package com.modnmetl.modnvote.domain.election;

import java.util.List;
import java.util.Optional;

/**
 * Immutable, generic definition of a linked-offices election.
 *
 * This is a pure data model. It does not implement voting, persistence of
 * multi-contest ballot content, counting, or GUI flow. Parsing lives in
 * {@link ElectionDefinitionParser} and validation in
 * {@link ElectionDefinitionValidator}.
 *
 * Office order and candidate order are preserved exactly as supplied so later
 * tranches can rely on a deterministic, source-defined ordering.
 *
 * @param model        the election model identifier (expected: {@link #LINKED_OFFICES_MODEL})
 * @param contests     contests/offices in source order
 * @param candidates   candidate definitions in source order
 * @param dependencies generic dependency rules between offices
 */
public record ElectionDefinition(
        String model,
        List<ContestDefinition> contests,
        List<CandidateDefinition> candidates,
        List<OfficeDependencyRule> dependencies
) {

    /**
     * The only election model currently recognised.
     */
    public static final String LINKED_OFFICES_MODEL = "LINKED_OFFICES";

    public ElectionDefinition {
        contests = contests == null ? List.of() : List.copyOf(contests);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public Optional<ContestDefinition> findContest(String officeKey) {
        return contests.stream()
                .filter(contest -> contest.officeKey().equals(officeKey))
                .findFirst();
    }

    public Optional<CandidateDefinition> findCandidate(String candidateKey) {
        return candidates.stream()
                .filter(candidate -> candidate.candidateKey().equals(candidateKey))
                .findFirst();
    }
}
