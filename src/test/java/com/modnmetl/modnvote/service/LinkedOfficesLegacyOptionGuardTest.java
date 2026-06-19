package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderService;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that the legacy {@code poll_options} authoring workflow is blocked for
 * {@link PollType#LINKED_OFFICES} polls (whose candidates live only in the
 * {@code ElectionDefinition} / {@code config_json}), while existing YES_NO,
 * RANKED_SINGLE_WINNER, builder, and JSON authoring paths continue to work.
 */
class LinkedOfficesLegacyOptionGuardTest {

    private static final Logger LOGGER = Logger.getLogger("LinkedOfficesLegacyOptionGuardTest");
    private static final String ACTOR = "tester";
    private static final String GUARD_MESSAGE =
            "LINKED_OFFICES candidates are managed through config_json and ElectionDefinition, not legacy poll options.";

    private static final String VALID_DEFINITION = """
            {
              "model": "LINKED_OFFICES",
              "offices": {
                "mayor": {"displayName": "Mayor", "method": "IRV", "seats": 1, "candidates": ["alice", "bob"]}
              },
              "candidateDefinitions": {
                "alice": {"displayName": "Alice", "eligibleFor": ["mayor"]},
                "bob": {"displayName": "Bob", "eligibleFor": ["mayor"]}
              }
            }
            """;

    private DatabaseManager freshDb(Path tempDir, String name) throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve(name));
        new SchemaInitializer(db).initialize();
        return db;
    }

    private PollService service(DatabaseManager db) {
        return new PollService(db, LOGGER);
    }

    private long newLinkedPoll(PollService service) throws Exception {
        return service.createPoll(ACTOR, PollType.LINKED_OFFICES);
    }

    // --- 1-5: legacy option mutations rejected for LINKED_OFFICES ------------

    @Test
    void addOptionRejectsLinkedOffices(@TempDir Path tempDir) throws Exception {
        PollService service = service(freshDb(tempDir, "guard-add.db"));
        long pollId = newLinkedPoll(service);

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.addOption(pollId, "alice", "Alice", "Candidate", ACTOR));
        assertEquals(GUARD_MESSAGE, ex.getMessage());
    }

    @Test
    void updateOptionNameRejectsLinkedOffices(@TempDir Path tempDir) throws Exception {
        PollService service = service(freshDb(tempDir, "guard-name.db"));
        long pollId = newLinkedPoll(service);

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.updateOptionName(pollId, 1L, "New Name", ACTOR));
        assertEquals(GUARD_MESSAGE, ex.getMessage());
    }

    @Test
    void updateOptionDescriptionRejectsLinkedOffices(@TempDir Path tempDir) throws Exception {
        PollService service = service(freshDb(tempDir, "guard-desc.db"));
        long pollId = newLinkedPoll(service);

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.updateOptionDescription(pollId, 1L, "New description", ACTOR));
        assertEquals(GUARD_MESSAGE, ex.getMessage());
    }

    @Test
    void moveOptionRejectsLinkedOffices(@TempDir Path tempDir) throws Exception {
        PollService service = service(freshDb(tempDir, "guard-move.db"));
        long pollId = newLinkedPoll(service);

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.moveOption(pollId, 1L, 0, ACTOR));
        assertEquals(GUARD_MESSAGE, ex.getMessage());
    }

    @Test
    void removeOptionRejectsLinkedOffices(@TempDir Path tempDir) throws Exception {
        PollService service = service(freshDb(tempDir, "guard-remove.db"));
        long pollId = newLinkedPoll(service);

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.removeOption(pollId, 1L, ACTOR));
        assertEquals(GUARD_MESSAGE, ex.getMessage());
    }

    // --- 6: YES_NO option editing still works -------------------------------

    @Test
    void yesNoOptionEditingStillWorks(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "guard-yesno.db");
        PollService service = service(db);
        long pollId = service.createPoll(ACTOR, PollType.YES_NO);

        List<PollOption> options = new PollOptionDao(db).findOptionsByPollId(pollId);
        assertEquals(2, options.size());
        long yesOptionId = options.get(0).optionId();

        service.updateOptionName(pollId, yesOptionId, "Absolutely", ACTOR);

        List<PollOption> updated = new PollOptionDao(db).findOptionsByPollId(pollId);
        assertTrue(updated.stream().anyMatch(o -> o.displayName().equals("Absolutely")));
    }

    // --- 7: RANKED_SINGLE_WINNER option editing still works ------------------

    @Test
    void rankedOptionEditingStillWorks(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "guard-ranked.db");
        PollService service = service(db);
        long pollId = service.createPoll(ACTOR, PollType.RANKED_SINGLE_WINNER);

        long optionId = service.addOption(pollId, "arabian", "Arabian", "A breed", ACTOR);
        service.updateOptionName(pollId, optionId, "Arabian Horse", ACTOR);
        service.updateOptionDescription(pollId, optionId, "Elegant", ACTOR);
        service.moveOption(pollId, optionId, 0, ACTOR);

        assertTrue(new PollOptionDao(db).findOptionsByPollId(pollId).stream()
                .anyMatch(o -> o.displayName().equals("Arabian Horse")));

        service.removeOption(pollId, optionId, ACTOR);
        assertFalse(new PollOptionDao(db).findOptionsByPollId(pollId).stream()
                .anyMatch(o -> o.optionId() == optionId));
    }

    // --- 8: builder save path still works -----------------------------------

    @Test
    void builderSavePathStillWorks(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "guard-builder.db");
        PollService service = service(db);
        LinkedOfficesBuilderService builderService = new LinkedOfficesBuilderService(service);

        long pollId = newLinkedPoll(service);

        LinkedOfficesBuilderState state = LinkedOfficesBuilderState.empty();
        var office = state.createOffice("mayor");
        office.setDisplayName("Mayor");
        office.setMethod(CountingMethod.IRV);
        office.setSeats(1);
        state.createCandidate("alice").setDisplayName("Alice");
        state.toggleEligibility("alice", "mayor");
        state.createCandidate("bob").setDisplayName("Bob");
        state.toggleEligibility("bob", "mayor");

        builderService.save(pollId, state, ACTOR);

        String stored = service.findPollById(pollId).configJson();
        assertEquals(builderService.serialize(state), stored);
        assertTrue(stored.contains("LINKED_OFFICES"));
    }

    // --- 9: JSON authoring (config import) path still works ------------------

    @Test
    void configImportPathStillWorks(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "guard-config.db");
        PollService service = service(db);
        long pollId = newLinkedPoll(service);

        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);

        assertEquals(VALID_DEFINITION, service.findPollById(pollId).configJson());
    }
}
