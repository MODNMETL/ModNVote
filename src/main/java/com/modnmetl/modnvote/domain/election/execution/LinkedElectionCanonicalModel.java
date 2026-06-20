package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixes the deterministic ordering rules for linked-election ballots so future
 * ballot hashing has a single, unambiguous canonical form to operate on.
 *
 * <p>This is <strong>not</strong> the final hash implementation and computes no
 * hashes. It only defines and applies ordering:
 * <ul>
 *   <li><b>Contest ordering</b> — responses are ordered by the election's defined
 *       contest (office) source order.</li>
 *   <li><b>Candidate ordering</b> — for ranked contests the voter's preference
 *       order is preserved (it is significant); for approval contests selections
 *       are reordered into the contest's defined candidate order (selection order
 *       is not significant). Any candidate key not present in the contest's
 *       defined list is appended after the known ones, preserving its submitted
 *       order, so even malformed input canonicalises deterministically.</li>
 *   <li><b>Response ordering</b> — at most one response per office is emitted; if
 *       a ballot contains duplicate responses for an office, only the first (in
 *       ballot order) is canonicalised.</li>
 * </ul>
 */
public final class LinkedElectionCanonicalModel {

    /**
     * @return the office keys in canonical contest order (definition source order)
     */
    public List<String> contestOrder(ElectionDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        List<String> order = new ArrayList<>();
        for (ContestDefinition contest : definition.contests()) {
            order.add(contest.officeKey());
        }
        return List.copyOf(order);
    }

    /**
     * @return the candidate keys for an office in canonical order (contest source
     * order), or an empty list if the office is unknown
     */
    public List<String> candidateOrder(ElectionDefinition definition, String officeKey) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(officeKey, "officeKey");
        return definition.findContest(officeKey)
                .map(ContestDefinition::candidateKeys)
                .map(List::copyOf)
                .orElse(List.of());
    }

    /**
     * Reduces a ballot to its deterministic canonical form. Input order of
     * responses and of approval selections does not affect the output.
     */
    public CanonicalBallot canonicalize(LinkedElectionBallot ballot) {
        Objects.requireNonNull(ballot, "ballot");
        ElectionDefinition definition = ballot.electionDefinition();

        List<CanonicalContestResponse> responses = new ArrayList<>();
        for (String officeKey : contestOrder(definition)) {
            ballot.findResponse(officeKey).ifPresent(vote -> {
                ContestDefinition contest = definition.findContest(officeKey).orElseThrow();
                List<String> ordered = canonicalCandidates(definition, officeKey, vote);
                responses.add(new CanonicalContestResponse(officeKey, contest.method(), ordered));
            });
        }

        return new CanonicalBallot(definition.model(), responses);
    }

    private List<String> canonicalCandidates(ElectionDefinition definition,
                                             String officeKey,
                                             ContestVote vote) {
        if (vote instanceof RankedContestVote ranked) {
            // Ranked order is significant; preserve it exactly.
            return List.copyOf(ranked.orderedCandidateKeys());
        }
        if (vote instanceof ApprovalContestVote approval) {
            // Approval selection order is not significant; normalise to contest order.
            List<String> definedOrder = candidateOrder(definition, officeKey);
            List<String> selected = approval.selectedCandidateKeys();
            List<String> canonical = new ArrayList<>();
            for (String candidateKey : definedOrder) {
                if (selected.contains(candidateKey)) {
                    canonical.add(candidateKey);
                }
            }
            // Unknown/extra keys (not in the defined list) follow, in submitted order.
            for (String candidateKey : selected) {
                if (!definedOrder.contains(candidateKey) && !canonical.contains(candidateKey)) {
                    canonical.add(candidateKey);
                }
            }
            return List.copyOf(canonical);
        }
        return List.of();
    }
}
