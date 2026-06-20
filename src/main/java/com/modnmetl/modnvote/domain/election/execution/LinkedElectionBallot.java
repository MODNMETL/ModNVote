package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.ElectionDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A complete in-memory ballot for a linked-offices election: the election
 * definition it was cast against plus the voter's per-contest responses.
 *
 * This is a transient, in-memory object. It is never persisted in this tranche;
 * there is no ballot submission, storage, or counting here. It exists so the
 * shape of a linked-election vote is formalized before the anonymous ballot
 * layer is touched.
 *
 * <p>The list of contest votes is order-insensitive for equality of intent:
 * {@link LinkedElectionCanonicalModel} defines the deterministic ordering used
 * for any future hashing. Multiple responses for the same office are not
 * rejected by construction — {@link LinkedElectionBallotValidator} reports them.
 *
 * @param electionDefinition the definition this ballot was cast against
 * @param contestVotes       the voter's responses, one (or more) per contest
 */
public record LinkedElectionBallot(
        ElectionDefinition electionDefinition,
        List<ContestVote> contestVotes
) {

    public LinkedElectionBallot {
        Objects.requireNonNull(electionDefinition, "electionDefinition");
        contestVotes = contestVotes == null ? List.of() : List.copyOf(contestVotes);
    }

    /**
     * @return the first response for the given office, if any
     */
    public Optional<ContestVote> findResponse(String officeKey) {
        return contestVotes.stream()
                .filter(vote -> vote.officeKey().equals(officeKey))
                .findFirst();
    }
}
