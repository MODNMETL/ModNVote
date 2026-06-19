package com.modnmetl.modnvote.domain.election;

import java.util.List;
import java.util.Objects;

/**
 * Generic candidate definition for a linked-offices election.
 *
 * A candidate may be eligible for one or more offices. Eligibility is generic;
 * no office name is hardcoded.
 *
 * @param candidateKey       stable candidate identifier (the candidateDefinitions map key)
 * @param displayName        human-facing name (may be null/blank until validated)
 * @param eligibleOfficeKeys the office keys this candidate may stand for, in source order
 */
public record CandidateDefinition(
        String candidateKey,
        String displayName,
        List<String> eligibleOfficeKeys
) {
    public CandidateDefinition {
        Objects.requireNonNull(candidateKey, "candidateKey");
        eligibleOfficeKeys = eligibleOfficeKeys == null ? List.of() : List.copyOf(eligibleOfficeKeys);
    }
}
