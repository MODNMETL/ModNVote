package com.modnmetl.modnvote.storage;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.AnonymousBallotContestResponse;
import com.modnmetl.modnvote.domain.Poll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Insert/read tests for {@link AnonymousBallotContestResponseDao}. Verifies that
 * ranked and approval rows persist correctly and read back in deterministic
 * canonical order ({@code response_id} ascending = insertion order).
 */
class AnonymousBallotContestResponseDaoTest {

    @Test
    void insertsAndReadsRankedAndApprovalRowsInCanonicalOrder(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve("dao.db"));
        new SchemaInitializer(dbm).initialize();

        PollDao pollDao = new PollDao(dbm);
        AnonymousBallotDao ballotDao = new AnonymousBallotDao(dbm);
        AnonymousBallotContestResponseDao responseDao = new AnonymousBallotContestResponseDao(dbm);

        long ballotId;
        try (Connection connection = dbm.getConnection()) {
            long pollId = pollDao.insertPoll(connection, linkedPoll(), "tester", "DEFAULT", "{}");
            ballotId = ballotDao.insertAnonymousBallot(
                    connection, pollId, "hash", "proof", "commit", Instant.ofEpochMilli(1000L));

            // Ranked Mayor: alice(1), bob(2), carol(3)
            // Approval Council (canonical order): alice(1), dave(2), grace(3)
            responseDao.insertResponses(connection, ballotId, List.of(
                    new AnonymousBallotContestResponseDao.NewContestResponse("mayor", "RANKED", "alice", 1, null),
                    new AnonymousBallotContestResponseDao.NewContestResponse("mayor", "RANKED", "bob", 2, null),
                    new AnonymousBallotContestResponseDao.NewContestResponse("mayor", "RANKED", "carol", 3, null),
                    new AnonymousBallotContestResponseDao.NewContestResponse("council", "APPROVAL", "alice", null, 1),
                    new AnonymousBallotContestResponseDao.NewContestResponse("council", "APPROVAL", "dave", null, 2),
                    new AnonymousBallotContestResponseDao.NewContestResponse("council", "APPROVAL", "grace", null, 3)
            ));
        }

        List<AnonymousBallotContestResponse> rows = responseDao.findResponsesByAnonymousBallotId(ballotId);
        assertEquals(6, rows.size());

        assertEquals(List.of("alice", "bob", "carol", "alice", "dave", "grace"),
                rows.stream().map(AnonymousBallotContestResponse::candidateKey).toList());
        assertEquals(List.of("mayor", "mayor", "mayor", "council", "council", "council"),
                rows.stream().map(AnonymousBallotContestResponse::officeKey).toList());

        AnonymousBallotContestResponse firstRanked = rows.get(0);
        assertEquals("RANKED", firstRanked.responseType());
        assertEquals(Integer.valueOf(1), firstRanked.rankPosition());
        assertNull(firstRanked.selectionOrder());

        AnonymousBallotContestResponse firstApproval = rows.get(3);
        assertEquals("APPROVAL", firstApproval.responseType());
        assertNull(firstApproval.rankPosition());
        assertEquals(Integer.valueOf(1), firstApproval.selectionOrder());
    }

    private static Poll linkedPoll() {
        return new Poll(
                0,
                "linked-dao-test",
                "Linked",
                "Description",
                PollType.LINKED_OFFICES,
                PollStatus.DRAFT,
                null,
                null,
                1,
                3,
                false,
                true,
                "secret"
        );
    }
}
