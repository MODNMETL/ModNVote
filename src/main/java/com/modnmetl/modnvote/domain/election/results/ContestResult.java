package com.modnmetl.modnvote.domain.election.results;

import com.modnmetl.modnvote.domain.election.CountingMethod;

import java.util.List;
import java.util.Objects;

/**
 * The computed result of a single contest/office within a linked-offices election.
 *
 * <p>Generic by construction: offices and candidates are identified only by their
 * keys, with no office name hardcoded. This is anonymous result data — it never
 * references a voter, ballot id, or proof phrase.
 *
 * @param officeKey            the contest/office key
 * @param displayName          the office display name (may equal the key)
 * @param method               the counting method applied
 * @param seats                the number of seats the contest fills
 * @param winners              elected candidate keys, in election order (IRV: the
 *                             single winner; approval: top-N by score then order)
 * @param candidateResults     every contest candidate's outcome, in contest order,
 *                             including dependency-excluded candidates
 * @param excludedCandidateKeys candidates excluded from this contest by a dependency
 *                             outcome, in contest order
 * @param exhaustedBallots     IRV ballots exhausted in the deciding round; 0 for
 *                             approval contests
 * @param rounds               IRV round snapshots in order; empty for approval contests
 * @param issues               deterministic, identity-free issues raised while counting
 */
public record ContestResult(
        String officeKey,
        String displayName,
        CountingMethod method,
        int seats,
        List<String> winners,
        List<CandidateResult> candidateResults,
        List<String> excludedCandidateKeys,
        int exhaustedBallots,
        List<IrvRoundResult> rounds,
        List<String> issues
) {
    public ContestResult {
        Objects.requireNonNull(officeKey, "officeKey");
        winners = winners == null ? List.of() : List.copyOf(winners);
        candidateResults = candidateResults == null ? List.of() : List.copyOf(candidateResults);
        excludedCandidateKeys = excludedCandidateKeys == null ? List.of() : List.copyOf(excludedCandidateKeys);
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
