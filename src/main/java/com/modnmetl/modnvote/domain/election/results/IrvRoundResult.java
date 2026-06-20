package com.modnmetl.modnvote.domain.election.results;

import java.util.List;
import java.util.Objects;

/**
 * A deterministic snapshot of one instant-runoff (IRV) counting round.
 *
 * <p>All ordering is derived from the contest's defined candidate order, never
 * from hashing or unordered iteration, so the same ballots always reproduce the
 * same round sequence. This is anonymous result data only.
 *
 * @param roundNumber            1-based round number
 * @param tallies                each continuing candidate's first-preference count
 *                               this round, in contest candidate order
 * @param continuingCandidates   candidate keys still in contention at the start of
 *                               this round, in contest candidate order
 * @param eliminatedCandidateKey the candidate eliminated at the end of this round,
 *                               or {@code null} if this was the deciding round
 * @param winnerCandidateKey     the candidate elected in this round (majority or
 *                               last-standing), or {@code null} if none was elected
 * @param exhaustedBallots       ballots that had a ranked response for this office
 *                               but no continuing candidate left to count this round
 */
public record IrvRoundResult(
        int roundNumber,
        List<CandidateTally> tallies,
        List<String> continuingCandidates,
        String eliminatedCandidateKey,
        String winnerCandidateKey,
        int exhaustedBallots
) {
    public IrvRoundResult {
        tallies = tallies == null ? List.of() : List.copyOf(tallies);
        continuingCandidates = continuingCandidates == null ? List.of() : List.copyOf(continuingCandidates);
    }
}
