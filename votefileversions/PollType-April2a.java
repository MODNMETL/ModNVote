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
    COMBINED_EXECUTIVE_AND_COUNCIL
}