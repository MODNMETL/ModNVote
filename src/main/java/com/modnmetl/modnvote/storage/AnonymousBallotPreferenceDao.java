package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.domain.BallotPreference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
}