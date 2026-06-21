package com.modnmetl.modnvote.domain.election.results;

import java.util.Objects;

/**
 * A single candidate's fractional vote value within one STV round or as a final
 * tally.
 *
 * <p>STV transfers ballots at fractional values, so unlike the integer
 * {@link CandidateTally} this carries the value as a deterministic decimal string
 * (a fixed-scale {@code BigDecimal} rendered with {@code toPlainString()}). It is
 * pure, anonymous result data: a candidate key and a value, with no voter
 * identity or ballot reference.
 *
 * @param candidateKey the candidate key being counted
 * @param value        the fractional value attributed to the candidate, as a
 *                     deterministic decimal string
 */
public record StvCandidateTally(
        String candidateKey,
        String value
) {
    public StvCandidateTally {
        Objects.requireNonNull(candidateKey, "candidateKey");
        Objects.requireNonNull(value, "value");
    }
}
