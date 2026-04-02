package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.DatabaseManager;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service layer for ballot submission and validation work.
 *
 * Atomic submission transactions, ballot hashing, receipts, and recount-linked
 * integrity operations will be introduced in the next implementation steps.
 */
public final class BallotService {

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;

    public BallotService(DatabaseManager databaseManager,
                         PlatformAdapter platformAdapter,
                         Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean isInitialized() {
        return true;
    }

    public String getStatusSummary() {
        return "BallotService ready";
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }

    public Logger getLogger() {
        return logger;
    }
}