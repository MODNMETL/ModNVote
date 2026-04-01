package com.modnmetl.modnvote.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Central SQLite access manager for ModNVote 2.0.
 *
 * This class owns the database path and creates fresh JDBC connections on demand.
 * Transactions for ballot submission and other atomic operations will be handled
 * at DAO/service level using a single acquired connection.
 */
public final class DatabaseManager {

    private final Path databasePath;
    private final String jdbcUrl;

    public DatabaseManager(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        ensureParentDirectoryExists();

        Connection connection = DriverManager.getConnection(jdbcUrl);
        applyPragmas(connection);
        return connection;
    }

    private void ensureParentDirectoryExists() {
        try {
            Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create database directory for " + databasePath, e);
        }
    }

    private void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public void close() {
        // Intentionally no-op for now.
        // SQLite connections are created per operation and closed by callers.
    }
}