package com.modnmetl.modnvote.ui.builder.election;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState.OfficeDraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests proving the builder save path goes through
 * {@link PollService} (never a DAO bypass), rejects invalid definitions, and
 * persists valid ones. No voting, ballot storage, or counting is involved.
 */
class LinkedOfficesBuilderServiceTest {

    private static final Logger LOGGER = Logger.getLogger("LinkedOfficesBuilderServiceTest");
    private static final String ACTOR = "tester";

    private DatabaseManager freshDb(Path tempDir, String name) throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve(name));
        new SchemaInitializer(db).initialize();
        return db;
    }

    private LinkedOfficesBuilderState validState() {
        LinkedOfficesBuilderState state = LinkedOfficesBuilderState.empty();
        OfficeDraft office = state.createOffice("mayor");
        office.setDisplayName("Mayor");
        office.setMethod(CountingMethod.IRV);
        office.setSeats(1);
        state.createCandidate("alice").setDisplayName("Alice");
        state.toggleEligibility("alice", "mayor");
        state.createCandidate("bob").setDisplayName("Bob");
        state.toggleEligibility("bob", "mayor");
        return state;
    }

    @Test
    void saveValidDefinitionPersistsThroughPollService(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "builder-save-valid.db");
        PollService pollService = new PollService(db, LOGGER);
        LinkedOfficesBuilderService builderService = new LinkedOfficesBuilderService(pollService);

        long pollId = pollService.createPoll(ACTOR, PollType.LINKED_OFFICES);
        LinkedOfficesBuilderState state = validState();

        builderService.save(pollId, state, ACTOR);

        String expected = builderService.serialize(state);
        assertEquals(expected, pollService.findPollById(pollId).configJson());
        assertTrue(expected.contains("LINKED_OFFICES"));
    }

    @Test
    void saveInvalidDefinitionIsRejectedAndNotPersisted(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "builder-save-invalid.db");
        PollService pollService = new PollService(db, LOGGER);
        LinkedOfficesBuilderService builderService = new LinkedOfficesBuilderService(pollService);

        long pollId = pollService.createPoll(ACTOR, PollType.LINKED_OFFICES);

        // Office with no eligible candidates is structurally invalid.
        LinkedOfficesBuilderState state = LinkedOfficesBuilderState.empty();
        OfficeDraft office = state.createOffice("mayor");
        office.setDisplayName("Mayor");
        office.setMethod(CountingMethod.IRV);
        office.setSeats(1);

        assertThrows(PollServiceException.class, () -> builderService.save(pollId, state, ACTOR));
        assertEquals("{}", pollService.findPollById(pollId).configJson(), "Invalid definition must not persist.");
    }

    @Test
    void saveRoutesThroughPollServiceGuards(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "builder-save-guard.db");
        PollService pollService = new PollService(db, LOGGER);
        LinkedOfficesBuilderService builderService = new LinkedOfficesBuilderService(pollService);

        // A non-LINKED_OFFICES poll must be rejected by PollService.updatePollConfigJson,
        // proving the builder save is not a DAO bypass.
        long yesNoId = pollService.createPoll(ACTOR, PollType.YES_NO);
        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> builderService.save(yesNoId, validState(), ACTOR));
        assertTrue(ex.getMessage().contains("LINKED_OFFICES"), "Unexpected message: " + ex.getMessage());
    }

    @Test
    void loadStateRoundTripsASavedDefinition(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "builder-load.db");
        PollService pollService = new PollService(db, LOGGER);
        LinkedOfficesBuilderService builderService = new LinkedOfficesBuilderService(pollService);

        long pollId = pollService.createPoll(ACTOR, PollType.LINKED_OFFICES);
        LinkedOfficesBuilderState saved = validState();
        builderService.save(pollId, saved, ACTOR);

        String storedJson = pollService.findPollById(pollId).configJson();
        LinkedOfficesBuilderState reloaded = builderService.loadState(storedJson);

        assertEquals(saved.toDefinition(), reloaded.toDefinition(),
                "Reloading a saved definition must reproduce the same election definition.");
    }

    @Test
    void loadStateOnBlankConfigReturnsEmptyBuffer(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "builder-blank.db");
        PollService pollService = new PollService(db, LOGGER);
        LinkedOfficesBuilderService builderService = new LinkedOfficesBuilderService(pollService);

        // A freshly created linked poll has config_json "{}" and must open an empty buffer.
        LinkedOfficesBuilderState state = builderService.loadState("{}");
        assertEquals(0, state.officeCount());
        assertEquals(0, state.candidateCount());
    }
}
