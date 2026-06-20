package com.modnmetl.modnvote.domain.election.execution;

import java.util.Objects;

/**
 * A single structured validation issue for a linked-election ballot.
 *
 * @param code      machine-readable failure category
 * @param officeKey the office the issue relates to, or {@code null} for ballot-level issues
 * @param message   a human-facing description (generic; no office name hardcoded)
 */
public record BallotValidationIssue(
        BallotValidationCode code,
        String officeKey,
        String message
) {
    public BallotValidationIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
