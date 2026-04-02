package com.modnmetl.modnvote.domain;

/**
 * A single ranked preference within a ballot.
 *
 * Example:
 * rankPosition=1, optionId=42
 */
public record BallotPreference(
        int rankPosition,
        long optionId
) {
    public BallotPreference {
        if (rankPosition < 1) {
            throw new IllegalArgumentException("rankPosition must be >= 1");
        }
        if (optionId < 1) {
            throw new IllegalArgumentException("optionId must be >= 1");
        }
    }
}