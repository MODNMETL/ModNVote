package com.modnmetl.modnvote.domain.election.execution;

/**
 * A voter's response to a single contest/office within a linked-offices election.
 *
 * This is a pure in-memory representation. It carries no persistence, counting,
 * or GUI behaviour and is never written to the anonymous ballot tables in this
 * tranche. A linked-election ballot ({@link LinkedElectionBallot}) is a list of
 * these, one per contest the voter responded to.
 *
 * <p>The vote shape must match the contest's counting method:
 * <ul>
 *   <li>{@link RankedContestVote} pairs with an IRV (ranked, single-seat) contest;</li>
 *   <li>{@link ApprovalContestVote} pairs with an APPROVAL_TOP_N contest.</li>
 * </ul>
 * This is a sealed type so future canonicalization and counting can switch over
 * the complete, closed set of response shapes.
 */
public sealed interface ContestVote permits RankedContestVote, ApprovalContestVote {

    /**
     * @return the office key this response is for (must match a contest in the
     * election definition; this is not enforced by construction)
     */
    String officeKey();
}
