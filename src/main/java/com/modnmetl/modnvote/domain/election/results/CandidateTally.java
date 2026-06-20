package com.modnmetl.modnvote.domain.election.results;

import java.util.Objects;

/**
 * A single candidate's vote count within one contest round or approval scoring.
 *
 * <p>This is a pure, anonymous result value: a candidate key and an integer count.
 * It carries no voter identity and no ballot reference.
 *
 * @param candidateKey the candidate key being counted
 * @param votes        the count attributed to that candidate (first preferences this
 *                     round for IRV; approvals for approval contests)
 */
public record CandidateTally(
        String candidateKey,
        int votes
) {
    public CandidateTally {
        Objects.requireNonNull(candidateKey, "candidateKey");
    }
}
