package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@code polls.config_json} and {@code poll_options.metadata_json}
 * are surfaced through the domain model and DAOs without any schema change.
 *
 * These are the read/carry paths future linked-offices tranches will rely on.
 */
class PollDefinitionPersistenceTest {

    private static Poll newPoll(String slug, String configJson) {
        return new Poll(
                0L,
                slug,
                "Title",
                "Description",
                PollType.RANKED_SINGLE_WINNER,
                PollStatus.DRAFT,
                null,
                null,
                3,
                1,
                true,
                true,
                "participation-secret",
                configJson
        );
    }

    private long insertPoll(DatabaseManager databaseManager, Poll poll) throws Exception {
        try (Connection connection = databaseManager.getConnection()) {
            return new PollDao(databaseManager)
                    .insertPoll(connection, poll, "tester", "UUID_AND_IP_HEURISTIC", poll.configJson());
        }
    }

    @Test
    void pollDaoPersistsAndReadsConfigJson(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("config-json.db"));
        new SchemaInitializer(databaseManager).initialize();
        PollDao pollDao = new PollDao(databaseManager);

        String configJson = "{\"model\":\"LINKED_OFFICES\",\"offices\":{}}";
        long pollId = insertPoll(databaseManager, newPoll("with-config", configJson));

        Poll loaded = pollDao.findPollById(pollId);
        assertNotNull(loaded);
        assertEquals(configJson, loaded.configJson());
    }

    @Test
    void pollDefaultsConfigJsonToEmptyObject(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("config-default.db"));
        new SchemaInitializer(databaseManager).initialize();
        PollDao pollDao = new PollDao(databaseManager);

        // Construct via the backward-compatible constructor (no configJson supplied).
        Poll poll = new Poll(
                0L, "default-config", "Title", "Description",
                PollType.YES_NO, PollStatus.DRAFT, null, null,
                1, 1, true, true, "participation-secret"
        );
        assertEquals("{}", poll.configJson());

        long pollId = insertPoll(databaseManager, poll);
        assertEquals("{}", pollDao.findPollById(pollId).configJson());
    }

    @Test
    void pollOptionDaoPersistsAndReadsMetadataJson(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("metadata-json.db"));
        new SchemaInitializer(databaseManager).initialize();
        PollDao pollDao = new PollDao(databaseManager);
        PollOptionDao optionDao = new PollOptionDao(databaseManager);

        long pollId = insertPoll(databaseManager, newPoll("with-options", "{}"));

        String metadataJson = "{\"eligibleFor\":[\"mayor\",\"council\"]}";
        try (Connection connection = databaseManager.getConnection()) {
            optionDao.insertOption(connection, pollId,
                    new PollOption(0L, pollId, "alice", "Alice", "Candidate", 0, metadataJson));
        }

        List<PollOption> options = optionDao.findOptionsByPollId(pollId);
        assertEquals(1, options.size());
        assertEquals(metadataJson, options.get(0).metadataJson());
    }

    @Test
    void pollOptionDefaultsMetadataJsonToEmptyObject(@TempDir Path tempDir) throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("metadata-default.db"));
        new SchemaInitializer(databaseManager).initialize();
        PollDao pollDao = new PollDao(databaseManager);
        PollOptionDao optionDao = new PollOptionDao(databaseManager);

        long pollId = insertPoll(databaseManager, newPoll("with-default-options", "{}"));

        // Construct via the backward-compatible constructor (no metadataJson supplied).
        PollOption option = new PollOption(0L, pollId, "bob", "Bob", "Candidate", 0);
        assertEquals("{}", option.metadataJson());

        try (Connection connection = databaseManager.getConnection()) {
            optionDao.insertOption(connection, pollId, option);
        }

        List<PollOption> options = optionDao.findOptionsByPollId(pollId);
        assertEquals(1, options.size());
        assertEquals("{}", options.get(0).metadataJson());
    }
}
