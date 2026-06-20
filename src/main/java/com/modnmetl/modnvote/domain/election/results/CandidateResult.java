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
 */
public record CandidateResult(
        String candidateKey,
        int score,
        boolean elected,
        boolean excluded,
        Integer eliminationRound
) {
    public CandidateResult {
        Objects.requireNonNull(candidateKey, "candidateKey");
    }
}
