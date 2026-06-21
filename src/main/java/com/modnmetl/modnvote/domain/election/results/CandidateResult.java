package com.modnmetl.modnvote.domain.election.results;

import java.util.Objects;

/**
 * The final outcome for one candidate within one contest.
 *
 * <p>Pure anonymous result data — a candidate key plus its computed standing. No
 * voter identity, ballot id, or proof material is ever carried here.
 *
 * @param candidateKey    the candidate key
 * @param score           the candidate's decisive score: the final-round IRV tally
 *                        for ranked contests, or the approval count for approval
 *                        contests; 0 for candidates excluded by a dependency
 * @param elected         whether the candidate filled a seat in this contest
 * @param excluded        whether the candidate was excluded from this contest by a
 *                        dependency outcome (e.g. they already won a preceding office)
 * @param eliminationRound for IRV, the 1-based round in which the candidate was
 *                        eliminated; {@code null} if they were elected, survived, or
 *                        the contest is not ranked
 * @param unresolved      whether the candidate is part of an approval tie at the seat
 *                        cutoff that could not be resolved by counting alone: such a
 *                        candidate is neither elected nor eliminated and awaits a
 *                        runoff/administrator resolution. Never {@code true} together
 *                        with {@code elected}.
 */
public record CandidateResult(
        String candidateKey,
        int score,
        boolean elected,
        boolean excluded,
        Integer eliminationRound,
        boolean unresolved
) {
    public CandidateResult {
        Objects.requireNonNull(candidateKey, "candidateKey");
    }

    /**
     * Backwards-compatible constructor for a fully resolved candidate (not part of an
     * unresolved cutoff tie).
     */
    public CandidateResult(String candidateKey, int score, boolean elected, boolean excluded,
                           Integer eliminationRound) {
        this(candidateKey, score, elected, excluded, eliminationRound, false);
    }
}
