package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.domain.BallotPreference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.List;
import java.util.Objects;

/**
 * DAO for ordered anonymous ballot preference persistence.
 */
public final class AnonymousBallotPreferenceDao {

    private final DatabaseManager databaseManager;

    public AnonymousBallotPreferenceDao(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public void insertPreferences(Connection connection,
                                  long anonymousBallotId,
                                  List<BallotPreference> preferences) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(preferences, "preferences");

        String sql = """
                INSERT INTO anonymous_ballot_preferences (
                    anonymous_ballot_id,
                    option_id,
                    rank_position
                ) VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (BallotPreference preference : preferences) {
                ps.setLong(1, anonymousBallotId);
                ps.setLong(2, preference.optionId());
                ps.setInt(3, preference.rankPosition());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public java.util.List<StoredAnonymousBallotPreference> findPreferencesByAnonymousBallotId(long anonymousBallotId)
            throws SQLException {
        String sql = """
            SELECT
                option_id,
                rank_position
            FROM anonymous_ballot_preferences
            WHERE anonymous_ballot_id = ?
            ORDER BY rank_position ASC
            """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, anonymousBallotId);

            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<StoredAnonymousBallotPreference> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    out.add(new StoredAnonymousBallotPreference(
                            rs.getLong("option_id"),
                            rs.getInt("rank_position")
                    ));
                }
                return out;
            }
        }
    }

    public record StoredAnonymousBallotPreference(
            long optionId,
            int rankPosition
    ) {
    }
}