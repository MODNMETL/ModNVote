package com.modnmetl.modnvote.service.canonical;

import com.modnmetl.modnvote.domain.Poll;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Single shared source of truth for anonymous-ballot canonicalization.
 *
 * The canonical payload is the exact byte sequence that is hashed to produce:
 * - the anonymous ballot hash (recount/integrity anchor)
 * - the ballot commitment hash (proof-phrase bearer verification)
 *
 * Design rules (ModNVote 2.x invariants):
 * - canonicalization is deterministic
 * - canonicalization depends ONLY on poll rule context and the anonymous,
 *   ordered option-id content of a ballot
 * - canonicalization is independent of player identity, UUID, name, IP address,
 *   session state, and participation records
 * - both {@code BallotService} (submission) and
 *   {@code IntegrityVerificationService} (recount/verification) MUST use this
 *   component so the two layers can never silently drift apart
 *
 * Compatibility:
 * - {@link #canonicalAnonymousBallotPayload(Poll, List, Instant)} reproduces the
 *   exact pre-2.2.0 payload format byte-for-byte for existing YES_NO and
 *   RANKED_SINGLE_WINNER ballots. It must not change without a snapshot version
 *   bump, or existing stored ballot hashes would fail verification.
 *
 * Forward design note (no implementation in this tranche):
 * - Linked Offices / multi-contest canonicalization will be added later as a
 *   separate method on this component. The existing single ordered-option-list
 *   format below must remain untouched so existing ballots keep verifying.
 */
public final class BallotCanonicalizer {

    /**
     * Canonical rule-snapshot version embedded in the payload.
     *
     * This is intentionally identical to the literal previously inlined in
     * {@code BallotService} and {@code IntegrityVerificationService}. Changing it
     * invalidates verification of all previously stored ballots.
     */
    private static final String RULE_SNAPSHOT_VERSION = "v2";

    /**
     * Builds the canonical anonymous-ballot payload for an ordered list of
     * option ids (the representation shared by YES_NO and RANKED_SINGLE_WINNER
     * ballots, where YES_NO is a single-element ordered list).
     *
     * @param poll            the poll whose rule context anchors the ballot
     * @param orderedOptionIds the anonymous, ordered option ids for the ballot
     * @param submittedAt     the ballot submission timestamp
     * @return the deterministic canonical payload string (no trailing newline)
     */
    public String canonicalAnonymousBallotPayload(Poll poll,
                                                  List<Long> orderedOptionIds,
                                                  Instant submittedAt) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(orderedOptionIds, "orderedOptionIds");
        Objects.requireNonNull(submittedAt, "submittedAt");

        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(poll.pollId()).append('\n');
        sb.append("poll_type=").append(poll.pollType().name()).append('\n');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli()).append('\n');
        sb.append("rule_snapshot_version=").append(RULE_SNAPSHOT_VERSION).append('\n');
        sb.append("max_rankings=").append(poll.maxRankings()).append('\n');
        sb.append("allow_partial_ranking=").append(poll.allowPartialRanking()).append('\n');
        sb.append("ordered_option_ids=").append(joinOptionIds(orderedOptionIds));
        return sb.toString();
    }

    private String joinOptionIds(List<Long> orderedOptionIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderedOptionIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(orderedOptionIds.get(i));
        }
        return sb.toString();
    }
}
