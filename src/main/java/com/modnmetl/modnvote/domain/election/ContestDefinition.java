package com.modnmetl.modnvote.domain.election;

import java.util.List;
import java.util.Objects;

/**
 * Generic contest/office definition for a linked-offices election.
 *
 * This models "one office to be filled" in a fully generic way. No office name
 * is hardcoded; Mayor/Council are only examples supplied through configuration.
 *
 * @param officeKey      stable office identifier (the offices map key)
 * @param displayName    human-facing office name (may be null/blank until validated)
 * @param method         the counting method (may be null until validated)
 * @param seats          number of seats to fill
 * @param maxSelections  maximum selections a voter may make (approval methods); null if not applicable
 * @param allowAbstain   whether a voter may abstain from this contest
 * @param candidateKeys  the candidate keys standing for this office, in source order
 */
public record ContestDefinition(
        String officeKey,
        String displayName,
        CountingMethod method,
        int seats,
        Integer maxSelections,
        boolean allowAbstain,
        List<String> candidateKeys
) {
    public ContestDefinition {
        Objects.requireNonNull(officeKey, "officeKey");
        candidateKeys = candidateKeys == null ? List.of() : List.copyOf(candidateKeys);
    }
}
