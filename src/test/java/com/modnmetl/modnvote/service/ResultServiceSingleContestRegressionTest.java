package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.BallotPreference;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.service.ResultService.PollResult;
import com.modnmetl.modnvote.storage.AnonymousBallotDao;
import com.modnmetl.modnvote.storage.AnonymousBallotPreferenceDao;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollDao;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard for the single-contest result path through {@link ResultService}.
 *
 * <p>Tranche 2K adds a separate LINKED_OFFICES result entry point and does not
 * change {@link ResultService#getPollResult(long)}. These tests pin the YES_NO and
 * RANKED_SINGLE_WINNER outcomes so that the linked-offices work cannot silently
 * alter the existing single-contest tally behaviour.
 */
class ResultServiceSingleContestRegressionTest {

    private static final Instant T = Instant.ofEpochMilli(1000L);

    @Test
    void yesNoResultIsUnchanged(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve("yesno.db"));
        new SchemaInitializer(dbm).initialize();
        ResultService resultService = new ResultService(dbm, Logger.getAnonymousLogger());

        PollOptionDao optionDao = new PollOptionDao(dbm);
        AnonymousBallotDao ballotDao = new AnonymousBallotDao(dbm);
        AnonymousBallotPreferenceDao preferenceDao = new AnonymousBallotPreferenceDao(dbm);

        long pollId;
        long yesId;
        long noId;
        try (Connection c = dbm.getConnection()) {
            pollId = new PollDao(dbm).insertPoll(c,
                    poll(0, "yn", PollType.YES_NO), "tester", "DEFAULT", "{}");
            yesId = optionDao.insertOption(c, pollId,
                    new PollOption(0, pollId, "yes", "Yes", "", 0));
            noId = optionDao.insertOption(c, pollId,
                    new PollOption(0, pollId, "no", "No", "", 1));

            // 3 yes, 1 no
            for (int i = 0; i < 3; i++) {
                long b = ballotDao.insertAnonymousBallot(c, pollId, "h" + i, "p" + i, "cm" + i, T);
                preferenceDao.insertPreferences(c, b, List.of(new BallotPreference(1, yesId)));
            }
            long b = ballotDao.insertAnonymousBallot(c, pollId, "hn", "pn", "cmn", T);
            preferenceDao.insertPreferences(c, b, List.of(new BallotPreference(1, noId)));
        }

        PollResult result = resultService.getPollResult(pollId);

        assertEquals(4, result.totalVotes());
        assertEquals("Yes", result.winnerName());
        assertEquals(3, tallyFor(result, "yes"));
        assertEquals(1, tallyFor(result, "no"));
    }

    @Test
    void rankedSingleWinnerResultIsUnchanged(@TempDir Path tempDir) throws Exception {
        DatabaseManager dbm = new DatabaseManager(tempDir.resolve("ranked.db"));
        new SchemaInitializer(dbm).initialize();
        ResultService resultService = new ResultService(dbm, Logger.getAnonymousLogger());

        PollOptionDao optionDao = new PollOptionDao(dbm);
        AnonymousBallotDao ballotDao = new AnonymousBallotDao(dbm);
        AnonymousBallotPreferenceDao preferenceDao = new AnonymousBallotPreferenceDao(dbm);

        long pollId;
        long a;
        long b;
        long cc;
        try (Connection c = dbm.getConnection()) {
            pollId = new PollDao(dbm).insertPoll(c,
                    poll(0, "rk", PollType.RANKED_SINGLE_WINNER), "tester", "DEFAULT", "{}");
            a = optionDao.insertOption(c, pollId, new PollOption(0, pollId, "a", "A", "", 0));
            b = optionDao.insertOption(c, pollId, new PollOption(0, pollId, "b", "B", "", 1));
            cc = optionDao.insertOption(c, pollId, new PollOption(0, pollId, "c", "C", "", 2));

            // A first: 2 ([A,B]); B first: 2 ([B,A]); C first: 1 ([C,A])
            insertRanked(ballotDao, preferenceDao, c, pollId, "1", List.of(a, b));
            insertRanked(ballotDao, preferenceDao, c, pollId, "2", List.of(a, b));
            insertRanked(ballotDao, preferenceDao, c, pollId, "3", List.of(b, a));
            insertRanked(ballotDao, preferenceDao, c, pollId, "4", List.of(b, a));
            insertRanked(ballotDao, preferenceDao, c, pollId, "5", List.of(cc, a));
        }

        PollResult result = resultService.getPollResult(pollId);

        // Round 1: A=2,B=2,C=1 -> eliminate C. Round 2: C transfers to A -> A=3 majority.
        assertEquals(5, result.totalVotes());
        assertEquals("A", result.winnerName());
        assertEquals(2, result.rankedChoiceRounds().size());
    }

    private static void insertRanked(AnonymousBallotDao ballotDao,
                                     AnonymousBallotPreferenceDao preferenceDao,
                                     Connection c, long pollId, String tag, List<Long> ordered) throws Exception {
        long b = ballotDao.insertAnonymousBallot(c, pollId, "h" + tag, "p" + tag, "cm" + tag, T);
        List<BallotPreference> prefs = new java.util.ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            prefs.add(new BallotPreference(i + 1, ordered.get(i)));
        }
        preferenceDao.insertPreferences(c, b, prefs);
    }

    private static int tallyFor(PollResult result, String key) {
        return result.tallies().stream()
                .filter(t -> t.optionKey().equals(key))
                .findFirst().orElseThrow().votes();
    }

    private static Poll poll(long pollId, String slug, PollType type) {
        return new Poll(pollId, slug, "Single Contest", "desc",
                type, PollStatus.CLOSED, null, null,
                3, 1, true, true, "secret-" + slug, "{}");
    }
}
