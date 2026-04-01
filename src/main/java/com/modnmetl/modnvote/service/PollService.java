package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.DatabaseManager;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service layer for poll lifecycle operations.
 *
 * This is intentionally a lightweight scaffold for the first 2.0 runtime phase.
 * Poll creation, open/close flows, and storage-backed retrieval will be added next.
 */
public final class PollService {

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;

    public PollService(DatabaseManager databaseManager,
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
        return "PollService ready";
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