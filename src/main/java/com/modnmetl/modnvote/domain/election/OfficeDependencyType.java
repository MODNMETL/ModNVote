package com.modnmetl.modnvote.domain.election;

/**
 * Types of generic dependency relationships between offices/contests.
 *
 * These are generic and must not be tied to any specific office name.
 */
public enum OfficeDependencyType {

    /**
     * Winners of the {@code fromOfficeKey} contest are excluded from the
     * {@code appliesToOfficeKey} contest when it is tallied. This implies the
     * source contest must be counted before the dependent contest.
     */
    EXCLUDE_WINNERS
}
