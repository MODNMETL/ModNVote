package com.modnmetl.modnvote.domain.election.results;

import java.util.List;
import java.util.Objects;

/**
 * The STV-specific portion of a {@link ContestResult}, present only for an
 * {@code STV} contest and {@code null} for IRV / approval contests.
 *
 * <p>Bundling the STV detail into a single nullable field keeps the shared
 * {@link ContestResult} record stable for the existing IRV and approval paths
 * while giving STV everything it needs to be displayed and published: the Droop
 * quota, the total exhausted ballot value, every candidate's precise final
 * fractional tally, and the round-by-round transfer/elimination summaries.
 *
 * <p>All values that can be fractional (quota, exhausted value, tallies) are
 * carried as deterministic decimal strings so the result is reproducible and is
 * never re-rounded by display code.
 *
 * @param quota          the Droop quota required to be elected, as a decimal string
 * @param exhaustedValue the total ballot value that exhausted (had no continuing
 *                       preference left to receive it), as a decimal string
 * @param finalTallies   every contest candidate's final fractional value, in
 *                       contest candidate order
 * @param rounds         the STV round snapshots in order
 */
public record StvResultData(
        String quota,
        String exhaustedValue,
        List<StvCandidateTally> finalTallies,
        List<StvRoundResult> rounds
) {
    public StvResultData {
        Objects.requireNonNull(quota, "quota");
        Objects.requireNonNull(exhaustedValue, "exhaustedValue");
        finalTallies = finalTallies == null ? List.of() : List.copyOf(finalTallies);
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }
}
