package com.modnmetl.modnvote.domain.election.execution;

import java.util.List;
import java.util.Objects;

/**
 * An approval (APPROVAL_TOP_N) response to a single contest.
 *
 * The voter selects a set of approved candidates. Selection order is not
 * semantically significant; canonicalization reorders selections into the
 * contest's defined candidate order so that two voters who approved the same
 * candidates produce an identical canonical response. The selections are stored
 * as a list (not a set) so the validator can detect and report duplicates rather
 * than silently collapsing them.
 *
 * @param officeKey             the contest/office this response is for
 * @param selectedCandidateKeys the approved candidate keys, as submitted
 */
public record ApprovalContestVote(
        String officeKey,
        List<String> selectedCandidateKeys
) implements ContestVote {

    public ApprovalContestVote {
        Objects.requireNonNull(officeKey, "officeKey");
        selectedCandidateKeys = selectedCandidateKeys == null ? List.of() : List.copyOf(selectedCandidateKeys);
    }
}
