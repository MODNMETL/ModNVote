package com.modnmetl.modnvote.domain.election.results;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete computed result of a linked-offices election.
 *
 * <p>This is anonymous result data only. It is derived solely from anonymous
 * ballot content and the election definition; it never references participation
 * records, player identity, or proof material. Contests appear in the
 * deterministic dependency-counting order that produced them.
 *
 * @param pollId         the poll the result was computed for
 * @param pollTitle      the poll title
 * @param complete       whether counting completed without a fatal structural
 *                       problem (a dependency cycle or an unusable definition);
 *                       individual contest issues do not by themselves clear this
 * @param countedBallots the number of anonymous ballots that were successfully
 *                       reconstructed and counted
 * @param skippedBallots the number of stored ballots skipped because they could not
 *                       be reconstructed (also recorded as issues)
 * @param contestResults the per-contest results in counting order
 * @param issues         deterministic, identity-free election-level issues
 */
public record LinkedElectionResult(
        long pollId,
        String pollTitle,
        boolean complete,
        int countedBallots,
        int skippedBallots,
        List<ContestResult> contestResults,
        List<String> issues
) {
    public LinkedElectionResult {
        Objects.requireNonNull(pollTitle, "pollTitle");
        contestResults = contestResults == null ? List.of() : List.copyOf(contestResults);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * @return the result for the given office, if the contest was counted
     */
    public Optional<ContestResult> findContest(String officeKey) {
        return contestResults.stream()
                .filter(contest -> contest.officeKey().equals(officeKey))
                .findFirst();
    }
}
