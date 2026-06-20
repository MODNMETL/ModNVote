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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rebuilds an in-memory {@link LinkedElectionBallot} from the anonymous contest
 * response rows stored for one anonymous ballot.
 *
 * <p>This is the recount/integrity foundation: it depends only on the election
 * definition and anonymous vote-content rows — no identity, no participation
 * data. Re-canonicalising the reconstructed ballot must reproduce the original
 * canonical payload (and therefore the original ballot hash) bit-for-bit, which
 * is what the round-trip tests assert. It does not perform counting or result
 * verification.
 *
 * <p><strong>Stored-data integrity:</strong> because the rows are recount input
 * the reconstructor is strict. It does not silently repair or skip malformed
 * rows; any structural inconsistency throws a
 * {@link LinkedBallotReconstructionException} so the integrity layer can surface
 * it. Specifically it rejects an office whose rows mix response types, rows with
 * missing/duplicate ordering positions, duplicate candidate rows, and unknown
 * response types.
 */
public final class LinkedBallotReconstructor {

    /**
     * Reconstructs the ballot from stored rows. Rows are grouped by office in
     * first-encounter order (the canonical contest order the DAO returns); within
     * an office, candidate order is taken from {@code rank_position} for ranked
     * responses and {@code selection_order} for approval responses, so
     * reconstruction is robust even if physical row order changes.
     *
     * @param definition the election definition the ballot was cast against
     * @param rows       the stored contest-response rows for one anonymous ballot
     * @return the reconstructed in-memory ballot
     * @throws LinkedBallotReconstructionException if the stored rows are malformed
     *                                             or internally inconsistent
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

            String responseType = singleResponseType(officeKey, officeRows);
            requireNoDuplicateCandidates(officeKey, officeRows);

            if (AnonymousBallotContestResponse.TYPE_RANKED.equals(responseType)) {
                List<String> ordered = orderBy(
                        officeKey, "rank_position", officeRows,
                        AnonymousBallotContestResponse::rankPosition);
                votes.add(new RankedContestVote(officeKey, ordered));
            } else if (AnonymousBallotContestResponse.TYPE_APPROVAL.equals(responseType)) {
                List<String> ordered = orderBy(
                        officeKey, "selection_order", officeRows,
                        AnonymousBallotContestResponse::selectionOrder);
                votes.add(new ApprovalContestVote(officeKey, ordered));
            } else {
                throw new LinkedBallotReconstructionException(
                        "office '" + officeKey + "' has unknown stored response type '" + responseType + "'.");
            }
        }

        return new LinkedElectionBallot(definition, votes);
    }

    /**
     * @return the single response type shared by every row of the office
     * @throws LinkedBallotReconstructionException if the office mixes response types
     */
    private String singleResponseType(String officeKey, List<AnonymousBallotContestResponse> officeRows) {
        String type = officeRows.get(0).responseType();
        for (AnonymousBallotContestResponse row : officeRows) {
            if (!Objects.equals(type, row.responseType())) {
                throw new LinkedBallotReconstructionException(
                        "office '" + officeKey + "' mixes response types '" + type
                                + "' and '" + row.responseType() + "'.");
            }
        }
        return type;
    }

    private void requireNoDuplicateCandidates(String officeKey, List<AnonymousBallotContestResponse> officeRows) {
        Set<String> seen = new LinkedHashSet<>();
        for (AnonymousBallotContestResponse row : officeRows) {
            if (!seen.add(row.candidateKey())) {
                throw new LinkedBallotReconstructionException(
                        "office '" + officeKey + "' has duplicate candidate row '" + row.candidateKey() + "'.");
            }
        }
    }

    /**
     * Orders the office rows by a positional field (rank or selection), requiring
     * every position to be present, positive, and unique. Returns the candidate
     * keys in ascending position order.
     */
    private List<String> orderBy(String officeKey,
                                 String fieldName,
                                 List<AnonymousBallotContestResponse> officeRows,
                                 java.util.function.Function<AnonymousBallotContestResponse, Integer> position) {
        Set<Integer> seenPositions = new LinkedHashSet<>();
        for (AnonymousBallotContestResponse row : officeRows) {
            Integer pos = position.apply(row);
            if (pos == null || pos < 1) {
                throw new LinkedBallotReconstructionException(
                        "office '" + officeKey + "' has a row with invalid " + fieldName
                                + " for candidate '" + row.candidateKey() + "'.");
            }
            if (!seenPositions.add(pos)) {
                throw new LinkedBallotReconstructionException(
                        "office '" + officeKey + "' has duplicate " + fieldName + " " + pos + ".");
            }
        }
        return officeRows.stream()
                .sorted(Comparator.comparingInt(position::apply))
                .map(AnonymousBallotContestResponse::candidateKey)
                .toList();
    }
}
