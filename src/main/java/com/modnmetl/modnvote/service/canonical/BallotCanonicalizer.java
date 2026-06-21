package com.modnmetl.modnvote.service.canonical;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.BallotValidationIssue;
import com.modnmetl.modnvote.domain.election.execution.BallotValidationResult;
import com.modnmetl.modnvote.domain.election.execution.CanonicalBallot;
import com.modnmetl.modnvote.domain.election.execution.CanonicalContestResponse;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallotValidator;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionCanonicalModel;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
 * Linked Offices / multi-contest canonicalization:
 * - {@link #canonicalLinkedOfficesBallotPayload(Poll, ElectionDefinition,
 *   LinkedElectionBallot, Instant)} produces a deterministic, versioned payload
 *   for a multi-contest linked-offices ballot. It is a separate method with its
 *   own snapshot version ({@code linked_offices_v1}); it does not touch and is
 *   not reachable from the single-contest format above, so existing YES_NO /
 *   RANKED_SINGLE_WINNER hashes remain byte-for-byte unchanged.
 * - It is canonicalization only: it stores, submits, and counts nothing. Nothing
 *   in production calls it yet; it exists so the hash input is pinned down ahead
 *   of the storage tranche.
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
     * Canonical payload version for linked-offices (multi-contest) ballots.
     *
     * This is deliberately distinct from {@link #RULE_SNAPSHOT_VERSION} so the
     * two payload families can evolve independently and can never be confused.
     * Changing it would invalidate any future stored linked-offices ballot hash.
     */
    private static final String LINKED_OFFICES_SNAPSHOT_VERSION = "linked_offices_v1";

    private final LinkedElectionBallotValidator linkedBallotValidator = new LinkedElectionBallotValidator();
    private final LinkedElectionCanonicalModel linkedCanonicalModel = new LinkedElectionCanonicalModel();

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

    /**
     * Builds the canonical payload for a linked-offices (multi-contest) ballot.
     *
     * <p>The ballot is validated against the election definition first
     * ({@link LinkedElectionBallotValidator}); an invalid ballot is rejected with
     * an {@link IllegalArgumentException} that names the failing issue codes and
     * messages. A valid ballot is then reduced to its canonical form
     * ({@link LinkedElectionCanonicalModel}) — contests in definition order,
     * ranked preferences preserved, approval selections normalised to contest
     * candidate order — and serialised into a deterministic text payload.
     *
     * <p>The payload depends only on the poll's rule context, the election
     * definition, and the anonymous ballot content. It contains no player UUID,
     * name, IP address, session state, or participation token, mirroring the
     * privacy invariants of {@link #canonicalAnonymousBallotPayload}.
     *
     * <p>This method is independent of the single-contest format and uses its own
     * {@code rule_snapshot_version=linked_offices_v1}; it cannot change existing
     * YES_NO / RANKED_SINGLE_WINNER payloads.
     *
     * @param poll        the linked-offices poll whose rule context anchors the ballot
     * @param definition  the election definition the ballot is cast against; must
     *                    equal the ballot's own definition
     * @param ballot      the in-memory linked-offices ballot
     * @param submittedAt the ballot submission timestamp
     * @return the deterministic canonical payload string (no trailing newline)
     * @throws IllegalArgumentException if the poll is not a linked-offices poll,
     *                                  the definition does not match the ballot's
     *                                  definition, or the ballot fails validation
     */
    public String canonicalLinkedOfficesBallotPayload(Poll poll,
                                                      ElectionDefinition definition,
                                                      LinkedElectionBallot ballot,
                                                      Instant submittedAt) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(ballot, "ballot");
        Objects.requireNonNull(submittedAt, "submittedAt");

        if (poll.pollType() != PollType.LINKED_OFFICES) {
            throw new IllegalArgumentException(
                    "linked-offices canonicalization requires a LINKED_OFFICES poll, got "
                            + poll.pollType());
        }
        if (!definition.equals(ballot.electionDefinition())) {
            throw new IllegalArgumentException(
                    "ballot election definition does not match the supplied definition");
        }

        // Hashing requires a valid ballot. Unlike the canonical model (which is
        // defensive), this path refuses to serialise an invalid ballot.
        BallotValidationResult validation = linkedBallotValidator.validate(ballot);
        if (!validation.valid()) {
            throw new IllegalArgumentException(
                    "cannot canonicalize an invalid linked-offices ballot: "
                            + describeIssues(validation.issues()));
        }

        CanonicalBallot canonical = linkedCanonicalModel.canonicalize(ballot);

        StringBuilder sb = new StringBuilder();
        sb.append("poll_id=").append(poll.pollId()).append('\n');
        sb.append("poll_type=").append(poll.pollType().name()).append('\n');
        sb.append("election_model=").append(canonical.model()).append('\n');
        sb.append("submitted_at=").append(submittedAt.toEpochMilli()).append('\n');
        sb.append("rule_snapshot_version=").append(LINKED_OFFICES_SNAPSHOT_VERSION).append('\n');
        sb.append("contest_count=").append(canonical.responses().size());
        for (CanonicalContestResponse response : canonical.responses()) {
            sb.append('\n');
            sb.append("contest=").append(response.officeKey());
            sb.append(";method=").append(response.method().name());
            sb.append(";type=").append(responseType(response.method()));
            sb.append(";candidates=").append(String.join(",", response.orderedCandidateKeys()));
        }
        return sb.toString();
    }

    private static String responseType(CountingMethod method) {
        return switch (method) {
            case IRV, STV -> "RANKED";
            case APPROVAL_TOP_N -> "APPROVAL";
        };
    }

    private static String describeIssues(List<BallotValidationIssue> issues) {
        return issues.stream()
                .map(issue -> issue.code() + (issue.officeKey() == null ? "" : "[" + issue.officeKey() + "]")
                        + ": " + issue.message())
                .collect(Collectors.joining("; "));
    }
}
