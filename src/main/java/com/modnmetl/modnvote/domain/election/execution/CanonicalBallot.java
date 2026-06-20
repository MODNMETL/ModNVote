package com.modnmetl.modnvote.domain.election.execution;

import java.util.List;
import java.util.Objects;

/**
 * A linked-election ballot reduced to a deterministic canonical form.
 *
 * <p>Responses are ordered by the election's defined contest order; within each
 * response, candidate keys follow the canonicalization rules described on
 * {@link LinkedElectionCanonicalModel}. Two ballots expressing the same intent
 * produce an equal {@code CanonicalBallot} regardless of the order in which the
 * voter's responses or approval selections were supplied.
 *
 * <p>This is canonicalization <em>planning</em>, not the final ballot-hash
 * implementation. It exists to fix ordering decisions now so later hashing is
 * unambiguous.
 *
 * @param model     the election model identifier
 * @param responses canonical responses in contest order
 */
public record CanonicalBallot(
        String model,
        List<CanonicalContestResponse> responses
) {
    public CanonicalBallot {
        Objects.requireNonNull(model, "model");
        responses = responses == null ? List.of() : List.copyOf(responses);
    }
}
