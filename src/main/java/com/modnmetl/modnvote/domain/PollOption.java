package com.modnmetl.modnvote.domain;

import java.util.Objects;

/**
 * A selectable option within a poll.
 *
 * Examples:
 * - YES / NO
 * - a horse breed
 * - a candidate in an election
 *
 * Options carry an opaque {@code metadataJson} payload sourced from the existing
 * {@code poll_options.metadata_json} column. For existing options this is "{}".
 * It is the forward-looking home for per-option/candidate metadata (for example,
 * the set of offices a candidate is eligible for in a linked offices election)
 * without requiring schema changes.
 */
public record PollOption(
        long optionId,
        long pollId,
        String key,
        String displayName,
        String description,
        int displayOrder,
        String metadataJson
) {
    private static final String DEFAULT_METADATA_JSON = "{}";

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
        if (metadataJson == null || metadataJson.isBlank()) {
            metadataJson = DEFAULT_METADATA_JSON;
        }
    }

    /**
     * Backward-compatible constructor that defaults {@code metadataJson} to "{}".
     *
     * Existing call sites that predate the metadata_json surfacing keep working
     * unchanged through this overload.
     */
    public PollOption(long optionId,
                      long pollId,
                      String key,
                      String displayName,
                      String description,
                      int displayOrder) {
        this(optionId, pollId, key, displayName, description, displayOrder, DEFAULT_METADATA_JSON);
    }
}
