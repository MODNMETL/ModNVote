package com.modnmetl.modnvote.domain.election;

/**
 * Raised when a linked-offices election definition cannot be parsed or fails
 * validation.
 *
 * Messages are intended to be admin-facing and should clearly state what is
 * wrong with the definition.
 */
public final class ElectionDefinitionException extends RuntimeException {

    public ElectionDefinitionException(String message) {
        super(message);
    }

    public ElectionDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
