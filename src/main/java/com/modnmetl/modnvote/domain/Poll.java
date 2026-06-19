package com.modnmetl.modnvote.domain;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;

import java.time.Instant;
import java.util.Objects;

/**
 * Core in-memory representation of a poll definition.
 *
 * Polls now also carry a per-poll participation secret used to derive
 * one-way participation tokens. This lets the system enforce inclusion
 * and duplicate prevention without storing identity alongside vote content.
 *
 * Polls additionally carry an opaque {@code configJson} payload sourced from the
 * existing {@code polls.config_json} column. For existing YES_NO and
 * RANKED_SINGLE_WINNER polls this is simply "{}". It is the forward-looking home
 * for richer poll/election definition data (for example, the generic linked
 * offices election definition) without requiring schema changes. The domain
 * model only carries the raw JSON; parsing/validation lives in dedicated
 * components in the service/domain layers.
 */
public final class Poll {

    private static final String DEFAULT_CONFIG_JSON = "{}";

    private final long pollId;
    private final String slug;
    private final String title;
    private final String description;
    private final PollType pollType;
    private final PollStatus status;
    private final Instant opensAt;
    private final Instant closesAt;
    private final int maxRankings;
    private final int seatCount;
    private final boolean allowPartialRanking;
    private final boolean requiresConfirmation;
    private final String participationSecret;
    private final String configJson;

    /**
     * Backward-compatible constructor that defaults {@code configJson} to "{}".
     *
     * Existing call sites that predate the config_json surfacing keep working
     * unchanged through this overload.
     */
    public Poll(long pollId,
                String slug,
                String title,
                String description,
                PollType pollType,
                PollStatus status,
                Instant opensAt,
                Instant closesAt,
                int maxRankings,
                int seatCount,
                boolean allowPartialRanking,
                boolean requiresConfirmation,
                String participationSecret) {
        this(pollId,
                slug,
                title,
                description,
                pollType,
                status,
                opensAt,
                closesAt,
                maxRankings,
                seatCount,
                allowPartialRanking,
                requiresConfirmation,
                participationSecret,
                DEFAULT_CONFIG_JSON);
    }

    public Poll(long pollId,
                String slug,
                String title,
                String description,
                PollType pollType,
                PollStatus status,
                Instant opensAt,
                Instant closesAt,
                int maxRankings,
                int seatCount,
                boolean allowPartialRanking,
                boolean requiresConfirmation,
                String participationSecret,
                String configJson) {
        this.pollId = pollId;
        this.slug = requireNonBlank(slug, "slug");
        this.title = requireNonBlank(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.pollType = Objects.requireNonNull(pollType, "pollType");
        this.status = Objects.requireNonNull(status, "status");
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.maxRankings = maxRankings;
        this.seatCount = seatCount;
        this.allowPartialRanking = allowPartialRanking;
        this.requiresConfirmation = requiresConfirmation;
        this.participationSecret = requireNonBlank(participationSecret, "participationSecret");
        this.configJson = normalizeConfigJson(configJson);

        if (maxRankings < 0) {
            throw new IllegalArgumentException("maxRankings must not be negative");
        }
        if (seatCount < 0) {
            throw new IllegalArgumentException("seatCount must not be negative");
        }
        if (opensAt != null && closesAt != null && closesAt.isBefore(opensAt)) {
            throw new IllegalArgumentException("closesAt must not be before opensAt");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeConfigJson(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_CONFIG_JSON;
        }
        return value;
    }

    public long pollId() {
        return pollId;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public PollType pollType() {
        return pollType;
    }

    public PollStatus status() {
        return status;
    }

    public Instant opensAt() {
        return opensAt;
    }

    public Instant closesAt() {
        return closesAt;
    }

    public int maxRankings() {
        return maxRankings;
    }

    public int seatCount() {
        return seatCount;
    }

    public boolean allowPartialRanking() {
        return allowPartialRanking;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    public String participationSecret() {
        return participationSecret;
    }

    public String configJson() {
        return configJson;
    }
}
