package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies {@link PollDao#updatePollConfigJson} writes only {@code config_json}
 * and leaves every other poll field untouched (no schema change).
 */
class PollConfigUpdatePersistenceTest {

    private static Poll newPoll() {
        return new Poll(
                0L,
                "config-update-slug",
                "Original Title",
                "Original Description",
                PollType.LINKED_OFFICES,
                PollStatus.DRAFT,
                null,
                null,
                0,
                1,
                true,
                true,
                "participation-secret",
                "{}"
        );
    }

    @Test
    void updatePollConfigJsonPersistsNewJsonAndKeepsOtherFields(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("config-update.db"));
        new SchemaInitializer(databaseManager).initialize();
        PollDao pollDao = new PollDao(databaseManager);

        long pollId;
        try (Connection connection = databaseManager.getConnection()) {
            pollId = pollDao.insertPoll(connection, newPoll(), "tester", "UUID_AND_IP_HEURISTIC", "{}");
        }

        Poll before = pollDao.findPollById(pollId);
        assertNotNull(before);
        assertEquals("{}", before.configJson());

        String newConfig = "{\"model\":\"LINKED_OFFICES\",\"offices\":{}}";
        try (Connection connection = databaseManager.getConnection()) {
            pollDao.updatePollConfigJson(connection, pollId, newConfig);
        }

        Poll after = pollDao.findPollById(pollId);
        assertNotNull(after);
        assertEquals(newConfig, after.configJson());

        // Every other field is unchanged.
        assertEquals(before.slug(), after.slug());
        assertEquals(before.title(), after.title());
        assertEquals(before.description(), after.description());
        assertEquals(before.pollType(), after.pollType());
        assertEquals(before.status(), after.status());
        assertEquals(before.maxRankings(), after.maxRankings());
        assertEquals(before.seatCount(), after.seatCount());
        assertEquals(before.allowPartialRanking(), after.allowPartialRanking());
        assertEquals(before.requiresConfirmation(), after.requiresConfirmation());
        assertEquals(before.participationSecret(), after.participationSecret());
    }
}
