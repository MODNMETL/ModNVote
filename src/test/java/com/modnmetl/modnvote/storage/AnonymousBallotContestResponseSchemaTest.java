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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural privacy + existence tests for the linked-offices anonymous content
 * table {@code anonymous_ballot_contest_responses}.
 *
 * <p>Guards the core invariant: this table holds anonymous vote content only,
 * carries no identity columns, and links solely to {@code anonymous_ballots} —
 * never to {@code participation_records}.
 */
class AnonymousBallotContestResponseSchemaTest {

    private static final String TABLE = "anonymous_ballot_contest_responses";

    private static final Set<String> FORBIDDEN_COLUMN_MARKERS = Set.of(
            "uuid", "identity", "floodgate", "participation", "player", "ip", "token", "receipt", "owner", "name"
    );

    @Test
    void tableExistsWithExpectedColumns(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = schema(tempDir, "columns.db");
        Set<String> columns = columnNames(dbm, TABLE);

        Set<String> expected = Set.of(
                "response_id",
                "anonymous_ballot_id",
                "office_key",
                "response_type",
                "candidate_key",
                "rank_position",
                "selection_order",
                "created_at"
        );
        assertEquals(expected, columns, "unexpected column set for " + TABLE);
    }

    @Test
    void tableContainsNoIdentityColumns(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = schema(tempDir, "privacy.db");
        for (String column : columnNames(dbm, TABLE)) {
            String normalized = column.toLowerCase(Locale.ROOT);
            for (String marker : FORBIDDEN_COLUMN_MARKERS) {
                assertFalse(normalized.contains(marker),
                        TABLE + " must not contain identity-bearing column '" + column + "'.");
            }
        }
    }

    @Test
    void tableForeignKeyReferencesOnlyAnonymousBallots(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = schema(tempDir, "fk.db");

        Set<String> referencedTables = new LinkedHashSet<>();
        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement("PRAGMA foreign_key_list(" + TABLE + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                referencedTables.add(rs.getString("table").toLowerCase(Locale.ROOT));
            }
        }

        assertEquals(Set.of("anonymous_ballots"), referencedTables,
                TABLE + " must reference only anonymous_ballots (no participation_records link).");
        assertFalse(referencedTables.contains("participation_records"),
                TABLE + " must not have a foreign key to participation_records.");
    }

    @Test
    void tableHasExpectedIndexesIncludingUniqueCandidate(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = schema(tempDir, "indexes.db");

        boolean hasBallotIndex = false;
        boolean hasBallotOfficeIndex = false;
        boolean hasUniqueCandidateIndex = false;

        try (Connection connection = dbm.getConnection();
             PreparedStatement ps = connection.prepareStatement("PRAGMA index_list(" + TABLE + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                boolean unique = rs.getInt("unique") == 1;
                if (name.equals("idx_ab_contest_responses_ballot")) {
                    hasBallotIndex = true;
                } else if (name.equals("idx_ab_contest_responses_ballot_office")) {
                    hasBallotOfficeIndex = true;
                } else if (name.equals("idx_ab_contest_responses_unique_candidate")) {
                    hasUniqueCandidateIndex = unique;
                }
            }
        }

        assertTrue(hasBallotIndex, "missing index on anonymous_ballot_id");
        assertTrue(hasBallotOfficeIndex, "missing index on (anonymous_ballot_id, office_key)");
        assertTrue(hasUniqueCandidateIndex,
                "missing UNIQUE index on (anonymous_ballot_id, office_key, candidate_key)");
    }

    private static DatabaseManager schema(Path tempDir, String name) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve(name));
        new SchemaInitializer(dbm).initialize();
        return dbm;
    }

    private static Set<String> columnNames(DatabaseManager dbm, String tableName) throws Exception {
        Set<String> columns = new LinkedHashSet<>();
        try (Connection connection = dbm.getConnection();
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
