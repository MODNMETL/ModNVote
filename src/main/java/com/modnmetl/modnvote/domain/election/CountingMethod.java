package com.modnmetl.modnvote.domain.election;

/**
 * Counting methods supported by the generic linked-offices election model.
 *
 * These are generic concepts and must not be tied to any specific office name.
 */
public enum CountingMethod {

    /**
     * Instant-runoff voting for a single-seat ranked contest.
     */
    IRV,

    /**
     * Approval voting where the top-N approved candidates fill the seats.
     */
    APPROVAL_TOP_N,

    /**
     * Single transferable vote for a multi-seat ranked contest. Voters rank
     * candidates exactly as they do for {@link #IRV}; the difference is purely in
     * the count (Droop quota, surplus transfers, eliminations) and in the number
     * of seats filled. STV therefore uses the same ranked ballot shape as IRV.
     */
    STV;

    /**
     * @return whether this method is filled by a ranked ballot (a
     * {@code RankedContestVote} stored as {@code RANKED} contest-response rows).
     * Both {@link #IRV} and {@link #STV} are ranked; {@link #APPROVAL_TOP_N} is not.
     * This is the single source of truth for "is this contest ranked?" so the
     * voter GUI, ballot validation, canonicalization and storage never drift.
     */
    public boolean usesRankedBallot() {
        return this == IRV || this == STV;
    }
}
