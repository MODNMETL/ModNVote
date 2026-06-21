package com.modnmetl.modnvote.domain.election.results;

import java.util.List;
import java.util.Objects;

/**
 * A deterministic snapshot of one single-transferable-vote (STV) counting round.
 *
 * <p>This is the STV analogue of {@link IrvRoundResult}; the IRV round model is
 * deliberately left unchanged. All ordering is derived from the contest's defined
 * candidate order, never from hashing or unordered iteration, so the same ballots
 * always reproduce the same round sequence. This is anonymous result data only.
 *
 * @param roundNumber            1-based round number
 * @param tallies                each continuing candidate's current fractional
 *                               value at the start of this round, in contest
 *                               candidate order
 * @param electedThisRound       candidate keys that met quota (or filled the last
 *                               seats) and were elected in this round, in the order
 *                               they were elected; empty if none
 * @param eliminatedCandidateKey the candidate eliminated at the end of this round,
 *                               or {@code null} if this round elected instead
 * @param summary                a short, deterministic, identity-free description
 *                               of what happened this round (quota election and
 *                               surplus transfer, elimination and transfer, or the
 *                               final auto-election of remaining candidates)
 */
public record StvRoundResult(
        int roundNumber,
        List<StvCandidateTally> tallies,
        List<String> electedThisRound,
        String eliminatedCandidateKey,
        String summary
) {
    public StvRoundResult {
        tallies = tallies == null ? List.of() : List.copyOf(tallies);
        electedThisRound = electedThisRound == null ? List.of() : List.copyOf(electedThisRound);
        Objects.requireNonNull(summary, "summary");
    }
}
