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
    APPROVAL_TOP_N
}
