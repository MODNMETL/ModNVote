package com.modnmetl.modnvote.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural privacy/non-joinability regression tests for the live schema.
 *
 * These tests run the real {@link SchemaInitializer} against a throwaway SQLite
 * database and assert that the anonymous vote-content tables carry no
 * identity-bearing columns, and that they share no linking column with the
 * identity-aware participation table.
 *
 * They guard the core ModNVote 2.x invariant: identity and vote content must
 * not be joinable.
 */
class AnonymousBallotSchemaPrivacyTest {

    /**
     * Substrings that would indicate an identity-bearing or cross-layer linking
     * column has leaked into anonymous vote-content tables.
     */
    private static final Set<String> FORBIDDEN_COLUMN_MARKERS = Set.of(
            "uuid",
            "identity",
            "floodgate",
            "participation",
            "player",
            "ip_hash",
            "receipt"
    );

    @Test
    void anonymousBallotTablesContainNoIdentityColumns(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("schema-privacy-test.db"));
        new SchemaInitializer(databaseManager).initialize();

        assertNoForbiddenColumns(databaseManager, "anonymous_ballots");
        assertNoForbiddenColumns(databaseManager, "anonymous_ballot_preferences");
    }

    @Test
    void participationAndAnonymousBallotTablesShareNoLinkingColumn(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("non-joinable-test.db"));
        new SchemaInitializer(databaseManager).initialize();

        Set<String> participationColumns = columnNames(databaseManager, "participation_records");
        Set<String> ballotColumns = columnNames(databaseManager, "anonymous_ballots");
        Set<String> preferenceColumns = columnNames(databaseManager, "anonymous_ballot_preferences");

        // poll_id (coarse grouping key) and submitted_at (coarse timestamp the
        // integrity layer relies on) are not identity and do not link a specific
        // voter to a specific ballot. Any OTHER shared column - e.g. a shared
        // receipt/token/proof key - would be a non-joinability risk.
        Set<String> benignSharedColumns = Set.of("poll_id", "submitted_at");
        Set<String> shared = new LinkedHashSet<>(participationColumns);
        shared.retainAll(ballotColumns);
        shared.removeAll(benignSharedColumns);
        assertTrue(shared.isEmpty(),
                "participation_records and anonymous_ballots must share no per-voter linking column "
                        + "besides coarse columns " + benignSharedColumns + ", found: " + shared);

        Set<String> sharedPreferences = new LinkedHashSet<>(participationColumns);
        sharedPreferences.retainAll(preferenceColumns);
        assertTrue(sharedPreferences.isEmpty(),
                "participation_records and anonymous_ballot_preferences must share no column, found: " + sharedPreferences);
    }

    private void assertNoForbiddenColumns(DatabaseManager databaseManager, String tableName) throws Exception {
        for (String column : columnNames(databaseManager, tableName)) {
            String normalized = column.toLowerCase(Locale.ROOT);
            for (String marker : FORBIDDEN_COLUMN_MARKERS) {
                assertFalse(normalized.contains(marker),
                        "Table " + tableName + " must not contain identity-bearing column '" + column + "'.");
            }
        }
    }

    private Set<String> columnNames(DatabaseManager databaseManager, String tableName) throws Exception {
        Set<String> columns = new LinkedHashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        assertFalse(columns.isEmpty(), "Expected table '" + tableName + "' to exist with columns.");
        return columns;
    }
}
