package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.storage.DatabaseManager;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.storage.SchemaInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tranche 2C tests: linked-offices authoring (config_json updates), poll
 * creation, and lifecycle readiness, with voting still impossible.
 */
class PollServiceLinkedOfficesTest {

    private static final Logger LOGGER = Logger.getLogger("PollServiceLinkedOfficesTest");
    private static final String ACTOR = "tester";

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

    // Parses, but IRV with 2 seats and too few eligible candidates is structurally invalid.
    private static final String INVALID_DEFINITION = """
            {
              "model": "LINKED_OFFICES",
              "offices": {
                "mayor": {"displayName": "Mayor", "method": "IRV", "seats": 2, "candidates": ["alice"]}
              },
              "candidateDefinitions": {
                "alice": {"displayName": "Alice", "eligibleFor": ["mayor"]}
              }
            }
            """;

    private PollService newService(DatabaseManager db) {
        return new PollService(db, LOGGER);
    }

    private DatabaseManager freshDb(Path tempDir, String name) throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve(name));
        new SchemaInitializer(db).initialize();
        return db;
    }

    private int countOptions(DatabaseManager db, long pollId) throws Exception {
        return new PollOptionDao(db).findOptionsByPollId(pollId).size();
    }

    private String latestConfigAuditPayload(DatabaseManager db, long pollId) throws Exception {
        String sql = "SELECT canonical_payload FROM audit_events WHERE poll_id = ? AND event_type = 'POLL_CONFIG_UPDATED'"
                + " ORDER BY sequence_no DESC LIMIT 1";
        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private long countAnonymousBallots(DatabaseManager db, long pollId) throws Exception {
        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM anonymous_ballots WHERE poll_id = ?")) {
            ps.setLong(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // --- Creation -----------------------------------------------------------

    @Test
    void createsLinkedOfficesAsDraftWithDefaultConfigAndNoOptions(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "create-linked.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        Poll poll = service.findPollById(pollId);

        assertNotNull(poll);
        assertEquals(PollType.LINKED_OFFICES, poll.pollType());
        assertEquals(PollStatus.DRAFT, poll.status());
        assertEquals("{}", poll.configJson());
        assertEquals(0, countOptions(db, pollId), "Linked offices polls must not gain default options.");
    }

    @Test
    void existingYesNoAndRankedCreationUnchanged(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "create-existing.db");
        PollService service = newService(db);

        long yesNoId = service.createPoll(ACTOR, PollType.YES_NO);
        Poll yesNo = service.findPollById(yesNoId);
        assertEquals(PollType.YES_NO, yesNo.pollType());
        assertEquals(PollStatus.DRAFT, yesNo.status());
        assertEquals("{}", yesNo.configJson());
        assertEquals(2, countOptions(db, yesNoId), "YES_NO keeps its canonical yes/no options.");

        long rankedId = service.createPoll(ACTOR, PollType.RANKED_SINGLE_WINNER);
        Poll ranked = service.findPollById(rankedId);
        assertEquals(PollType.RANKED_SINGLE_WINNER, ranked.pollType());
        assertEquals(PollStatus.DRAFT, ranked.status());
        assertEquals(0, countOptions(db, rankedId));
    }

    // --- Config update ------------------------------------------------------

    @Test
    void acceptsAndPersistsValidConfigOnDraftLinkedPoll(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "config-valid.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);

        assertEquals(VALID_DEFINITION, service.findPollById(pollId).configJson());
    }

    @Test
    void rejectsInvalidConfigAndDoesNotPersist(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "config-invalid.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);

        assertThrows(PollServiceException.class,
                () -> service.updatePollConfigJson(pollId, INVALID_DEFINITION, ACTOR));

        assertEquals("{}", service.findPollById(pollId).configJson(), "Invalid config must not be written.");
    }

    @Test
    void rejectsConfigUpdateOnNonDraftPoll(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "config-nondraft.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollDescription(pollId, "A linked offices election.", ACTOR);
        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);
        service.readyPoll(pollId, ACTOR);

        assertEquals(PollStatus.READY, service.findPollById(pollId).status());

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR));
        assertTrue(ex.getMessage().contains("DRAFT"), "Unexpected message: " + ex.getMessage());
    }

    @Test
    void rejectsConfigUpdateOnNonLinkedPoll(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "config-nonlinked.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.YES_NO);
        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR));
        assertTrue(ex.getMessage().contains("LINKED_OFFICES"), "Unexpected message: " + ex.getMessage());
    }

    @Test
    void auditEventOmitsRawConfigJson(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "config-audit.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);

        String payload = latestConfigAuditPayload(db, pollId);
        assertNotNull(payload, "Expected a POLL_CONFIG_UPDATED audit event.");
        assertTrue(payload.contains("config_hash="), "Audit payload should include a config hash.");
        assertTrue(payload.contains("config_bytes="), "Audit payload should include the byte length.");
        assertTrue(payload.contains("model=LINKED_OFFICES"), "Audit payload should include the model.");
        // The raw definition contents must not be present in the audit log.
        assertFalse(payload.contains("candidateDefinitions"), "Audit payload must not contain raw config JSON.");
        assertFalse(payload.contains("alice"), "Audit payload must not contain raw config JSON.");
    }

    // --- Lifecycle readiness -----------------------------------------------

    @Test
    void validLinkedDefinitionPassesValidation(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "ready-valid.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollDescription(pollId, "A linked offices election.", ACTOR);
        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);

        PollService.PollValidationResult result = service.validatePollDefinition(pollId);
        assertTrue(result.valid(), "Issues: " + result.issues());
    }

    @Test
    void missingDefinitionFailsValidation(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "ready-missing.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollDescription(pollId, "A linked offices election.", ACTOR);

        PollService.PollValidationResult result = service.validatePollDefinition(pollId);
        assertFalse(result.valid());
        assertFalse(result.issues().isEmpty());
    }

    @Test
    void readyPollSucceedsForValidLinkedDefinition(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "ready-success.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollDescription(pollId, "A linked offices election.", ACTOR);
        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);
        service.readyPoll(pollId, ACTOR);

        assertEquals(PollStatus.READY, service.findPollById(pollId).status());
    }

    @Test
    void readyPollFailsForInvalidLinkedDefinition(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "ready-fail.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollDescription(pollId, "A linked offices election.", ACTOR);

        assertThrows(PollServiceException.class, () -> service.readyPoll(pollId, ACTOR));
        assertEquals(PollStatus.DRAFT, service.findPollById(pollId).status());
    }

    @Test
    void openPollRejectsLinkedOfficesEvenWhenReady(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "open-reject.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.LINKED_OFFICES);
        service.updatePollDescription(pollId, "A linked offices election.", ACTOR);
        service.updatePollConfigJson(pollId, VALID_DEFINITION, ACTOR);
        service.readyPoll(pollId, ACTOR);

        PollServiceException ex = assertThrows(PollServiceException.class,
                () -> service.openPoll(pollId, ACTOR));
        assertTrue(ex.getMessage().contains("Linked Offices voting is not implemented yet"),
                "Unexpected message: " + ex.getMessage());

        assertEquals(PollStatus.READY, service.findPollById(pollId).status(), "Poll must remain READY, not OPEN.");
        assertEquals(0, countAnonymousBallots(db, pollId), "No ballots may exist for a linked offices poll.");
    }

    @Test
    void existingYesNoReadyAndOpenStillWork(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = freshDb(tempDir, "yesno-lifecycle.db");
        PollService service = newService(db);

        long pollId = service.createPoll(ACTOR, PollType.YES_NO);
        service.updatePollDescription(pollId, "Do you agree?", ACTOR);
        service.readyPoll(pollId, ACTOR);
        assertEquals(PollStatus.READY, service.findPollById(pollId).status());

        service.openPoll(pollId, ACTOR);
        assertEquals(PollStatus.OPEN, service.findPollById(pollId).status());
    }
}
