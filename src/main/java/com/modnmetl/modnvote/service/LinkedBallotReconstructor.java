package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rebuilds an in-memory {@link LinkedElectionBallot} from the anonymous contest
 * response rows stored for one anonymous ballot.
 *
 * <p>This is the recount/debugging foundation: it depends only on the election
 * definition and anonymous vote-content rows — no identity, no participation
 * data. Re-canonicalising the reconstructed ballot must reproduce the original
 * canonical payload (and therefore the original ballot hash) bit-for-bit, which
 * is what the round-trip tests assert. It does not perform counting or result
 * verification.
 */
public final class LinkedBallotReconstructor {

    /**
     * Reconstructs the ballot from stored rows (assumed to be in canonical order,
     * as the DAO returns them). Candidate order within an office is taken from
     * {@code rank_position} for ranked responses and {@code selection_order} for
     * approval responses, so reconstruction is robust even if row order changes.
     *
     * @param definition the election definition the ballot was cast against
     * @param rows       the stored contest-response rows for one anonymous ballot
     * @return the reconstructed in-memory ballot
     */
    public LinkedElectionBallot reconstruct(ElectionDefinition definition,
                                            List<AnonymousBallotContestResponse> rows) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(rows, "rows");

        // Preserve first-encounter office order (which is canonical contest order).
        Map<String, List<AnonymousBallotContestResponse>> byOffice = new LinkedHashMap<>();
        for (AnonymousBallotContestResponse row : rows) {
            byOffice.computeIfAbsent(row.officeKey(), k -> new ArrayList<>()).add(row);
        }

        List<ContestVote> votes = new ArrayList<>();
        for (Map.Entry<String, List<AnonymousBallotContestResponse>> entry : byOffice.entrySet()) {
            String officeKey = entry.getKey();
            List<AnonymousBallotContestResponse> officeRows = entry.getValue();
            String responseType = officeRows.get(0).responseType();

            if (AnonymousBallotContestResponse.TYPE_RANKED.equals(responseType)) {
                List<String> ordered = officeRows.stream()
                        .sorted(Comparator.comparing(r -> orderKey(r.rankPosition())))
                        .map(AnonymousBallotContestResponse::candidateKey)
                        .toList();
                votes.add(new RankedContestVote(officeKey, ordered));
            } else if (AnonymousBallotContestResponse.TYPE_APPROVAL.equals(responseType)) {
                List<String> ordered = officeRows.stream()
                        .sorted(Comparator.comparing(r -> orderKey(r.selectionOrder())))
                        .map(AnonymousBallotContestResponse::candidateKey)
                        .toList();
                votes.add(new ApprovalContestVote(officeKey, ordered));
            } else {
                throw new IllegalStateException("Unknown stored response type '" + responseType
                        + "' for office '" + officeKey + "'.");
            }
        }

        return new LinkedElectionBallot(definition, votes);
    }

    private static int orderKey(Integer position) {
        return position == null ? Integer.MAX_VALUE : position;
    }
}
