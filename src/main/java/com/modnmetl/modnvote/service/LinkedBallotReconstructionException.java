package com.modnmetl.modnvote.service;

/**
 * Thrown when stored anonymous contest-response rows cannot be reconstructed into
 * a well-formed {@link com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot}.
 *
 * <p>This signals a <em>stored-data integrity</em> problem (malformed, mixed, or
 * inconsistent rows), not an ordinary voter mistake. It is an
 * {@link IllegalStateException} so callers that only care that reconstruction
 * failed can catch the broader type, while integrity verification can catch this
 * specific type to attribute the failure precisely.
 *
 * <p>Messages never contain voter identity — only office/candidate keys and the
 * structural problem found.
 */
public final class LinkedBallotReconstructionException extends IllegalStateException {

    public LinkedBallotReconstructionException(String message) {
        super(message);
    }
}
