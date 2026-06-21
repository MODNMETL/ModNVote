package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import com.modnmetl.modnvote.ui.session.VoteSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards confirming that {@link PollType#LINKED_OFFICES} — which is fully votable as
 * of 2.2.0 through its own dedicated multi-contest paths — never leaks into the
 * single-contest result and vote-session paths (which cannot represent a
 * multi-contest election), and that the existing single-contest types are
 * unaffected. Linked-offices voting, counting, results and witness publication are
 * covered by the dedicated linked-offices service and lifecycle tests.
 */
class LinkedOfficesGuardTest {

    private static final Logger LOGGER = Logger.getLogger("LinkedOfficesGuardTest");

    private long insertPoll(DatabaseManager db, PollType type, PollStatus status, List<PollOption> options) throws Exception {
        Poll poll = new Poll(
                0L, "slug-" + UUID.randomUUID(), "Title", "Description",
                type, status, null, null, 1, 1, true, true, "secret"
        );
        try (Connection connection = db.getConnection()) {
            long pollId = new PollDao(db).insertPoll(connection, poll, "tester", "POLICY", poll.configJson());
            new PollOptionDao(db).insertOptions(connection, pollId, options);
            return pollId;
        }
    }

    @Test
    void resultServiceRejectsLinkedOffices(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("linked-result.db"));
        new SchemaInitializer(db).initialize();

        long pollId = insertPoll(db, PollType.LINKED_OFFICES, PollStatus.CLOSED,
                List.of(new PollOption(0L, 0L, "alice", "Alice", "Candidate", 0)));

        ResultService resultService = new ResultService(db, LOGGER);
        PollServiceException ex = assertThrows(PollServiceException.class, () -> resultService.getPollResult(pollId));
        assertTrue(ex.getMessage().contains("multi-contest result"),
                "Unexpected message: " + ex.getMessage());
    }

    @Test
    void resultServiceStillHandlesYesNo(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("yesno-result.db"));
        new SchemaInitializer(db).initialize();

        long pollId = insertPoll(db, PollType.YES_NO, PollStatus.CLOSED, List.of(
                new PollOption(0L, 0L, "yes", "Yes", "Affirmative", 0),
                new PollOption(0L, 0L, "no", "No", "Negative", 1)
        ));

        ResultService resultService = new ResultService(db, LOGGER);
        ResultService.PollResult result = resultService.getPollResult(pollId);
        assertEquals(PollType.YES_NO, result.pollType());
    }

    @Test
    void voteSessionCannotBeCreatedForLinkedOffices() {
        Poll linkedPoll = new Poll(
                1L, "linked", "Linked", "Desc",
                PollType.LINKED_OFFICES, PollStatus.OPEN, null, null,
                1, 1, true, true, "secret"
        );
        List<PollOption> options = List.of(new PollOption(0L, 0L, "alice", "Alice", "Candidate", 0));

        assertThrows(IllegalArgumentException.class,
                () -> new VoteSession(UUID.randomUUID(), linkedPoll, options));
    }

    @Test
    void pollTypeParsingRemainsBackwardCompatible() {
        assertEquals(PollType.YES_NO, PollType.valueOf("YES_NO"));
        assertEquals(PollType.RANKED_SINGLE_WINNER, PollType.valueOf("RANKED_SINGLE_WINNER"));
        assertEquals(PollType.LINKED_OFFICES, PollType.valueOf("LINKED_OFFICES"));
    }
}
