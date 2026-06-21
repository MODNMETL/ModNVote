package com.modnmetl.modnvote.api;

/**
 * The supported poll and election engines planned for ModNVote 2.x.
 *
 * The early 2.0 implementation will focus first on ranked single-winner polls
 * for the horse-breed use case, while keeping later election engines in the
 * shared type system from the start.
 */
public enum PollType {
    YES_NO,
    SINGLE_CHOICE,
    RANKED_SINGLE_WINNER,
    RANKED_MULTI_WINNER_STV,
    COMBINED_EXECUTIVE_AND_COUNCIL,
    /**
     * Generic linked-offices election (multiple contests in one election).
     *
     * Fully votable as of 2.2.0. Admins author a definition (via JSON {@code set},
     * file {@code import}, or the in-game definition builder), validate it, mark the
     * poll ready and open it; players vote through {@code /modnvote vote}; ballots are
     * submitted anonymously and yield a one-time proof phrase; results are computed,
     * displayed and published after close. Per-office {@code EXCLUDE_WINNERS}
     * dependencies are applied at counting time, never at voting time. The
     * single-contest result shape ({@code ResultService#getPollResult}) does not
     * represent a multi-contest election, so the linked path uses its own
     * result/witness entry points.
     */
    LINKED_OFFICES
}