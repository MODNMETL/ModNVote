package com.modnmetl.modnvote.domain.election.execution;

import java.util.List;
import java.util.Objects;

/**
 * The structured outcome of validating a {@link LinkedElectionBallot}.
 *
 * Validation never throws for ordinary voter mistakes; those are reported here
 * as issues. A result with no issues is {@link #valid()}.
 *
 * @param issues all validation issues in a deterministic order, empty if valid
 */
public record BallotValidationResult(List<BallotValidationIssue> issues) {

    public BallotValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * @return a result with no issues
     */
    public static BallotValidationResult empty() {
        return new BallotValidationResult(List.of());
    }

    /**
     * @return true if there are no issues
     */
    public boolean valid() {
        return issues.isEmpty();
    }

    /**
     * @return true if any issue has the given code
     */
    public boolean hasCode(BallotValidationCode code) {
        Objects.requireNonNull(code, "code");
        return issues.stream().anyMatch(issue -> issue.code() == code);
    }
}
