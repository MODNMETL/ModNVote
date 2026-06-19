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
     * Reserved and intentionally NON-VOTABLE as of 2.2.0: admins can validate a
     * linked-offices definition, but there is no authoring, voting, submission,
     * counting, or result path for this type yet. Guards in the command, ballot,
     * and result layers reject it.
     */
    LINKED_OFFICES
}