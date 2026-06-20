package com.modnmetl.modnvote.domain.election.execution;

/**
 * Machine-readable categories of linked-election ballot validation failures.
 *
 * These let callers (future vote sessions, counting) branch on the failure kind
 * without parsing human-facing text. They are generic; no office name is
 * encoded here.
 */
public enum BallotValidationCode {

    /** The response references an office key that does not exist in the definition. */
    UNKNOWN_OFFICE,

    /** The response shape does not match the contest's counting method. */
    WRONG_VOTE_TYPE,

    /** The voter responded to the same office more than once. */
    DUPLICATE_RESPONSE,

    /** A selected/ranked candidate key does not exist in the definition. */
    UNKNOWN_CANDIDATE,

    /** A candidate key appears more than once within a single response. */
    DUPLICATE_CANDIDATE,

    /** A candidate is not eligible for the office they were selected/ranked for. */
    INELIGIBLE_CANDIDATE,

    /** An approval response selected more candidates than the contest's maxSelections allows. */
    EXCEEDS_MAX_SELECTIONS,

    /** A dependency rule in the definition references an office that does not exist. */
    UNRESOLVED_DEPENDENCY
}
