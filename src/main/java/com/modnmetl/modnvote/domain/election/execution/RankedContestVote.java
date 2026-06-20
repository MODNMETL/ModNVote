package com.modnmetl.modnvote.domain.election.execution;

import java.util.List;
import java.util.Objects;

/**
 * A ranked (IRV) response to a single contest.
 *
 * The voter's preferences are an ordered list from most to least preferred. The
 * order is significant and is preserved exactly; canonicalization must not
 * reorder a ranked response. Duplicate or unknown candidate keys are not
 * rejected by construction — {@link LinkedElectionBallotValidator} reports those
 * as structured validation issues.
 *
 * @param officeKey            the contest/office this response is for
 * @param orderedCandidateKeys preferences from most to least preferred, in voter order
 */
public record RankedContestVote(
        String officeKey,
        List<String> orderedCandidateKeys
) implements ContestVote {

    public RankedContestVote {
        Objects.requireNonNull(officeKey, "officeKey");
        orderedCandidateKeys = orderedCandidateKeys == null ? List.of() : List.copyOf(orderedCandidateKeys);
    }
}
