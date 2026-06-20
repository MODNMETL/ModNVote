package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DAO for anonymous multi-contest ballot responses (linked-offices vote content).
 *
 * <p>Rows are anonymous vote content linked only to {@code anonymous_ballot_id}.
 * This DAO performs no identity-aware queries and never joins to
 * {@code participation_records}; it is the multi-contest analogue of
 * {@link AnonymousBallotPreferenceDao}.
 *
 * <p>Rows are inserted in canonical order (see {@code LinkedElectionCanonicalModel})
 * and read back ordered by {@code response_id}, which preserves that canonical
 * order deterministically for recount/reconstruction.
 */
public final class AnonymousBallotContestResponseDao {

    private final DatabaseManager databaseManager;

    public AnonymousBallotContestResponseDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    /**
     * Inserts the given canonical responses for one anonymous ballot, in list
     * order. The list must already be in canonical order.
     */
    public void insertResponses(Connection connection,
                                long anonymousBallotId,
                                List<NewContestResponse> responses) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(responses, "responses");

        String sql = """
                INSERT INTO anonymous_ballot_contest_responses (
                    anonymous_ballot_id,
                    office_key,
                    response_type,
                    candidate_key,
                    rank_position,
                    selection_order
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (NewContestResponse response : responses) {
                ps.setLong(1, anonymousBallotId);
                ps.setString(2, response.officeKey());
                ps.setString(3, response.responseType());
                ps.setString(4, response.candidateKey());
                if (response.rankPosition() != null) {
                    ps.setInt(5, response.rankPosition());
                } else {
                    ps.setNull(5, Types.INTEGER);
                }
                if (response.selectionOrder() != null) {
                    ps.setInt(6, response.selectionOrder());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * @return all responses for the ballot in deterministic canonical order
     * ({@code response_id} ascending, i.e. insertion order)
     */
    public List<AnonymousBallotContestResponse> findResponsesByAnonymousBallotId(long anonymousBallotId)
            throws SQLException {
        String sql = """
                SELECT
                    response_id,
                    anonymous_ballot_id,
                    office_key,
                    response_type,
                    candidate_key,
                    rank_position,
                    selection_order,
                    created_at
                FROM anonymous_ballot_contest_responses
                WHERE anonymous_ballot_id = ?
                ORDER BY response_id ASC
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, anonymousBallotId);

            try (ResultSet rs = ps.executeQuery()) {
                List<AnonymousBallotContestResponse> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new AnonymousBallotContestResponse(
                            rs.getLong("response_id"),
                            rs.getLong("anonymous_ballot_id"),
                            rs.getString("office_key"),
                            rs.getString("response_type"),
                            rs.getString("candidate_key"),
                            nullableInt(rs, "rank_position"),
                            nullableInt(rs, "selection_order"),
                            rs.getString("created_at")
                    ));
                }
                return out;
            }
        }
    }

    /**
     * Deletes all responses for the given anonymous ballot. (CASCADE already
     * removes them when the ballot is deleted; this is for explicit cleanup.)
     */
    public void deleteByAnonymousBallotId(Connection connection, long anonymousBallotId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        String sql = "DELETE FROM anonymous_ballot_contest_responses WHERE anonymous_ballot_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, anonymousBallotId);
            ps.executeUpdate();
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Insert input for one canonical contest response row. {@code rankPosition}
     * and {@code selectionOrder} are mutually exclusive: ranked rows set the
     * former, approval rows set the latter.
     */
    public record NewContestResponse(
            String officeKey,
            String responseType,
            String candidateKey,
            Integer rankPosition,
            Integer selectionOrder
    ) {
        public NewContestResponse {
            Objects.requireNonNull(officeKey, "officeKey");
            Objects.requireNonNull(responseType, "responseType");
            Objects.requireNonNull(candidateKey, "candidateKey");
        }
    }
}
