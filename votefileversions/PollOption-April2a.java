package com.modnmetl.modnvote.domain;

import java.util.Objects;

/**
 * A selectable option within a poll.
 *
 * Examples:
 * - YES / NO
 * - a horse breed
 * - a candidate in an election
 */
public record PollOption(
        long optionId,
        long pollId,
        String key,
        String displayName,
        String description,
        int displayOrder
) {
    public PollOption {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");

        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
    }
}