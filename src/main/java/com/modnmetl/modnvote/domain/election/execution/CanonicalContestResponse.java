package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.CountingMethod;

import java.util.List;
import java.util.Objects;

/**
 * A single contest response in canonical form: the office, its counting method,
 * and the candidate keys in their canonical order.
 *
 * <p>For ranked contests the order is the voter's preference order (significant
 * and preserved). For approval contests the order is the contest's defined
 * candidate order (selection order is not significant, so it is normalised).
 *
 * @param officeKey            the contest/office key
 * @param method               the contest's counting method
 * @param orderedCandidateKeys candidate keys in canonical order
 */
public record CanonicalContestResponse(
        String officeKey,
        CountingMethod method,
        List<String> orderedCandidateKeys
) {
    public CanonicalContestResponse {
        Objects.requireNonNull(officeKey, "officeKey");
        orderedCandidateKeys = orderedCandidateKeys == null ? List.of() : List.copyOf(orderedCandidateKeys);
    }
}
