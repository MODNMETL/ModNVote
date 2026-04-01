package com.modnmetl.modnvote.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Initializes the ModNVote 2.0 schema.
 *
 * This is a clean-break schema and does not attempt to migrate the old 1.x
 * participant/tally model.
 */
public final class SchemaInitializer {

    private final DatabaseManager databaseManager;

    public SchemaInitializer(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public void initialize() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS polls (
                        poll_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        slug TEXT NOT NULL UNIQUE,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        poll_type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_by TEXT,
                        created_at INTEGER NOT NULL,
                        opens_at INTEGER,
                        closes_at INTEGER,
                        max_rankings INTEGER NOT NULL,
                        seat_count INTEGER NOT NULL,
                        allow_partial_ranking INTEGER NOT NULL,
                        requires_confirmation INTEGER NOT NULL,
                        identity_policy TEXT NOT NULL,
                        config_json TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS poll_options (
                        option_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER NOT NULL,
                        option_key TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        display_order INTEGER NOT NULL,
                        icon_type TEXT NOT NULL,
                        icon_value TEXT NOT NULL,
                        metadata_json TEXT NOT NULL,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ballots (
                        ballot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER NOT NULL,
                        voter_uuid TEXT NOT NULL,
                        voter_name_snapshot TEXT NOT NULL,
                        identity_key TEXT NOT NULL,
                        identity_type TEXT NOT NULL,
                        ip_hash TEXT,
                        floodgate_id TEXT,
                        submitted_at INTEGER NOT NULL,
                        client_platform TEXT NOT NULL,
                        ballot_hash TEXT NOT NULL,
                        receipt_hash TEXT NOT NULL,
                        is_valid INTEGER NOT NULL,
                        invalid_reason TEXT,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ballot_preferences (
                        ballot_id INTEGER NOT NULL,
                        option_id INTEGER NOT NULL,
                        rank_position INTEGER NOT NULL,
                        PRIMARY KEY (ballot_id, rank_position),
                        FOREIGN KEY (ballot_id) REFERENCES ballots(ballot_id) ON DELETE CASCADE,
                        FOREIGN KEY (option_id) REFERENCES poll_options(option_id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_events (
                        event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER,
                        sequence_no INTEGER NOT NULL,
                        event_type TEXT NOT NULL,
                        canonical_payload TEXT NOT NULL,
                        prev_hash TEXT,
                        event_hash TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS seal_checkpoints (
                        checkpoint_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER NOT NULL,
                        sequence_no INTEGER NOT NULL,
                        ballot_count INTEGER NOT NULL,
                        state_hash TEXT NOT NULL,
                        signature_or_mac TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS external_publications (
                        publication_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER NOT NULL,
                        checkpoint_id INTEGER,
                        target_name TEXT NOT NULL,
                        target_type TEXT NOT NULL,
                        response_ref TEXT,
                        published_at INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        error_message TEXT,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE,
                        FOREIGN KEY (checkpoint_id) REFERENCES seal_checkpoints(checkpoint_id) ON DELETE SET NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_poll_options_poll_id
                    ON poll_options(poll_id)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_ballots_poll_id
                    ON ballots(poll_id)
                    """);

            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_ballots_poll_voter_uuid
                    ON ballots(poll_id, voter_uuid)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_ballot_preferences_option_id
                    ON ballot_preferences(option_id)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_audit_events_poll_sequence
                    ON audit_events(poll_id, sequence_no)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_seal_checkpoints_poll_sequence
                    ON seal_checkpoints(poll_id, sequence_no)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_external_publications_poll_id
                    ON external_publications(poll_id)
                    """);
        }
    }
}