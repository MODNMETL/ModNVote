package com.modnmetl.modnvote.service;

/**
 * Controlled exception for poll lifecycle failures.
 *
 * This ensures we do not leak low-level SQL or platform exceptions
 * directly into command/UI layers.
 */
public class PollServiceException extends Exception {

    public PollServiceException(String message) {
        super(message);
    }

    public PollServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}