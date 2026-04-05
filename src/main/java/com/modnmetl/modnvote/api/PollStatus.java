package com.modnmetl.modnvote.api;

/**
 * Lifecycle state for a poll.
 *
 * READY means:
 * - the poll has been fully configured
 * - validation has passed
 * - it is safe to open later
 */
public enum PollStatus {
    DRAFT,
    READY,
    SCHEDULED,
    OPEN,
    CLOSED,
    ARCHIVED
}