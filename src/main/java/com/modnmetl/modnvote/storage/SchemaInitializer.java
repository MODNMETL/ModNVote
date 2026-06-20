package com.modnmetl.modnvote.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Initializes the ModNVote 2.0 schema.
 *
 * Privacy model (v2 hardened):
 *
 * - participation_records:
 *     identity-aware, NO vote content
 *
 * - anonymous_ballots:
 *     vote content, NO identity linkage
 *
 * - NO shared receipt linkage between the two layers
 *
 * - ballot proof system:
 *     allows users to verify their exact ballot later without
 *     enabling identity linkage in storage
 */
public final class SchemaInitializer {

    private final DatabaseManager databaseManager;

    public SchemaInitializer(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public void initialize() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            // ------------------------------------------------------------------
            // POLLS
            // ------------------------------------------------------------------
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
                        participation_secret TEXT NOT NULL,
                        identity_policy TEXT NOT NULL,
                        config_json TEXT NOT NULL
                    )
                    """);

            // ------------------------------------------------------------------
            // POLL OPTIONS
            // ------------------------------------------------------------------
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

            // ------------------------------------------------------------------
            // PARTICIPATION (IDENTITY-AWARE, NO BALLOT CONTENT)
            // ------------------------------------------------------------------
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS participation_records (
                        participation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER NOT NULL,
                        participation_token_hash TEXT NOT NULL,
                        submitted_at INTEGER NOT NULL,
                        participation_receipt_hash TEXT NOT NULL,
                        client_platform TEXT NOT NULL,
                        ip_hash TEXT,
                        floodgate_id TEXT,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE
                    )
                    """);

            // ------------------------------------------------------------------
            // ANONYMOUS BALLOTS (CONTENT, NO IDENTITY LINKAGE)
            // ------------------------------------------------------------------
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS anonymous_ballots (
                        anonymous_ballot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        poll_id INTEGER NOT NULL,
                        ballot_hash TEXT NOT NULL,
                        ballot_proof_hash TEXT NOT NULL UNIQUE,
                        ballot_commitment_hash TEXT NOT NULL,
                        submitted_at INTEGER NOT NULL,
                        FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE
                    )
                    """);

            // ------------------------------------------------------------------
            // BALLOT PREFERENCES
            // ------------------------------------------------------------------
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS anonymous_ballot_preferences (
                        anonymous_ballot_id INTEGER NOT NULL,
                        option_id INTEGER NOT NULL,
                        rank_position INTEGER NOT NULL,
                        PRIMARY KEY (anonymous_ballot_id, rank_position),
                        FOREIGN KEY (anonymous_ballot_id) REFERENCES anonymous_ballots(anonymous_ballot_id) ON DELETE CASCADE,
                        FOREIGN KEY (option_id) REFERENCES poll_options(option_id) ON DELETE CASCADE
                    )
                    """);

            // ------------------------------------------------------------------
            // LINKED-OFFICES ANONYMOUS CONTEST RESPONSES
            // (multi-contest vote content for one anonymous ballot; NO identity)
            // ------------------------------------------------------------------
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS anonymous_ballot_contest_responses (
                        response_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        anonymous_ballot_id INTEGER NOT NULL,
                        office_key TEXT NOT NULL,
                        response_type TEXT NOT NULL,
                        candidate_key TEXT NOT NULL,
                        rank_position INTEGER,
                        selection_order INTEGER,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (anonymous_ballot_id) REFERENCES anonymous_ballots(anonymous_ballot_id) ON DELETE CASCADE
                    )
                    """);

            // ------------------------------------------------------------------
            // AUDIT EVENTS
            // ------------------------------------------------------------------
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

            // ------------------------------------------------------------------
            // SEAL CHECKPOINTS (future external verification)
            // ------------------------------------------------------------------
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

            // ------------------------------------------------------------------
            // EXTERNAL PUBLICATIONS
            // ------------------------------------------------------------------
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

            // ------------------------------------------------------------------
            // INDEXES
            // ------------------------------------------------------------------

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_poll_options_poll_id
                    ON poll_options(poll_id)
                    """);

            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_participation_records_poll_token
                    ON participation_records(poll_id, participation_token_hash)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_participation_records_poll_id
                    ON participation_records(poll_id)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_anonymous_ballots_poll_id
                    ON anonymous_ballots(poll_id)
                    """);

            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_anonymous_ballots_ballot_hash
                    ON anonymous_ballots(ballot_hash)
                    """);

            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_anonymous_ballots_proof_hash
                    ON anonymous_ballots(ballot_proof_hash)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_anonymous_ballot_preferences_option_id
                    ON anonymous_ballot_preferences(option_id)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_ab_contest_responses_ballot
                    ON anonymous_ballot_contest_responses(anonymous_ballot_id)
                    """);

            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_ab_contest_responses_ballot_office
                    ON anonymous_ballot_contest_responses(anonymous_ballot_id, office_key)
                    """);

            // Prevents the same candidate appearing twice within one office's
            // response on a single anonymous ballot.
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_ab_contest_responses_unique_candidate
                    ON anonymous_ballot_contest_responses(anonymous_ballot_id, office_key, candidate_key)
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