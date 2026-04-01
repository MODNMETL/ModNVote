package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.platform.PlatformAdapter;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service layer for poll lifecycle operations.
 */
public final class PollService {

    private final DatabaseManager databaseManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final PollDao pollDao;
    private final PollOptionDao pollOptionDao;

    public PollService(DatabaseManager databaseManager,
                       PlatformAdapter platformAdapter,
                       Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.platformAdapter = Objects.requireNonNull(platformAdapter, "platformAdapter");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.pollDao = new PollDao(databaseManager);
        this.pollOptionDao = new PollOptionDao(databaseManager);
    }

    public boolean isInitialized() {
        return true;
    }

    public String getStatusSummary() {
        return "PollService ready";
    }

    public List<Poll> listPolls() throws Exception {
        return pollDao.findAllPolls();
    }

    public long createSeedBreedPoll(String createdBy) throws Exception {
        String slug = "breed-of-the-month-" + Instant.now().toEpochMilli();

        if (pollDao.pollExistsBySlug(slug)) {
            throw new IllegalStateException("Generated slug already exists: " + slug);
        }

        Poll poll = new Poll(
                0L,
                slug,
                "Breed of the Month",
                "Rank the nominated horse breeds in order of preference.",
                PollType.RANKED_SINGLE_WINNER,
                PollStatus.DRAFT,
                null,
                null,
                6,
                1,
                true,
                true
        );

        List<PollOption> options = List.of(
                new PollOption(0L, 0L, "arabian", "Arabian", "Elegant, fast, and refined.", 0),
                new PollOption(0L, 0L, "shire", "Shire", "Large, powerful, and steady.", 1),
                new PollOption(0L, 0L, "mustang", "Mustang", "Hardy, agile, and spirited.", 2),
                new PollOption(0L, 0L, "friesian", "Friesian", "Striking black coat and noble bearing.", 3),
                new PollOption(0L, 0L, "andalusian", "Andalusian", "Strong, responsive, and graceful.", 4),
                new PollOption(0L, 0L, "clydesdale", "Clydesdale", "Heavy draft strength with calm temperament.", 5)
        );

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long pollId = pollDao.insertPoll(connection, poll, createdBy, "UUID_AND_IP_HEURISTIC", "{}");
                pollOptionDao.insertOptions(connection, pollId, options);
                connection.commit();
                return pollId;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
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