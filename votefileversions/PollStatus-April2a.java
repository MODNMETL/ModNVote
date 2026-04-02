package com.modnmetl.modnvote.api;

/**
 * Lifecycle state for a poll.
 */
public enum PollStatus {
    DRAFT,
    SCHEDULED,
    OPEN,
    CLOSED,
    ARCHIVED
}