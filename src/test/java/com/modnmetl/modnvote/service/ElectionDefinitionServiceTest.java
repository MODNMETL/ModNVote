package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ElectionDefinitionService} — the read-only admin validation
 * boundary. The service has no database dependency, so it cannot (and does not)
 * perform any persistence writes; these tests run entirely in memory.
 */
class ElectionDefinitionServiceTest {

    private static final String VALID_JSON = """
            {
              "model": "LINKED_OFFICES",
              "offices": {
                "mayor": { "displayName": "Mayor", "seats": 1, "method": "IRV", "candidates": ["alice", "bob"] },
                "council": { "displayName": "Council", "seats": 1, "method": "APPROVAL_TOP_N", "maxSelections": 2,
                             "excludeWinnersFrom": ["mayor"], "candidates": ["alice", "bob"] }
              },
              "candidateDefinitions": {
                "alice": { "displayName": "Alice", "eligibleFor": ["mayor", "council"] },
                "bob":   { "displayName": "Bob",   "eligibleFor": ["mayor", "council"] }
              }
            }
            """;

    private final ElectionDefinitionService service = new ElectionDefinitionService();

    @Test
    void validExampleReturnsValid() {
        ElectionDefinitionService.ElectionDefinitionValidationResult result = service.validate(VALID_JSON);

        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
        assertTrue(result.definition().isPresent());
        assertEquals("LINKED_OFFICES", result.rawModel().orElse(null));
    }

    @Test
    void emptyConfigReturnsInvalidWithMissingModelIssue() {
        ElectionDefinitionService.ElectionDefinitionValidationResult result = service.validate("{}");

        assertFalse(result.valid());
        assertTrue(result.rawModel().isEmpty());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.toLowerCase().contains("model")),
                "Expected a missing-model issue, got: " + result.issues());
    }

    @Test
    void malformedJsonReturnsInvalidWithUsefulIssue() {
        ElectionDefinitionService.ElectionDefinitionValidationResult result = service.validate("{ not json");

        assertFalse(result.valid());
        assertFalse(result.issues().isEmpty());
    }

    @Test
    void unknownModelReturnsInvalid() {
        String json = VALID_JSON.replace("\"LINKED_OFFICES\"", "\"SOMETHING_ELSE\"");
        ElectionDefinitionService.ElectionDefinitionValidationResult result = service.validate(json);

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.toLowerCase().contains("model")));
    }

    @Test
    void structurallyInvalidDefinitionReturnsIssues() {
        // IRV office given 2 seats is structurally invalid.
        String json = """
                {
                  "model": "LINKED_OFFICES",
                  "offices": {
                    "mayor": { "displayName": "Mayor", "seats": 2, "method": "IRV", "candidates": ["alice", "bob"] }
                  },
                  "candidateDefinitions": {
                    "alice": { "displayName": "Alice", "eligibleFor": ["mayor"] },
                    "bob":   { "displayName": "Bob",   "eligibleFor": ["mayor"] }
                  }
                }
                """;
        ElectionDefinitionService.ElectionDefinitionValidationResult result = service.validate(json);

        assertFalse(result.valid());
        assertTrue(result.definition().isPresent(), "definition should parse even when validation fails");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("exactly 1 seat")));
    }

    @Test
    void isLinkedOfficesModelDetectsModelString() {
        assertTrue(service.isLinkedOfficesModel(VALID_JSON));
        assertFalse(service.isLinkedOfficesModel("{}"));
        assertFalse(service.isLinkedOfficesModel("{\"model\":\"YES_NO\"}"));
        assertFalse(service.isLinkedOfficesModel(null));
    }

    @Test
    void validatePollUsesPollConfigJsonAndNeedsNoDatabase() {
        Poll poll = new Poll(
                7L, "linked", "Linked Poll", "Desc",
                PollType.LINKED_OFFICES, PollStatus.DRAFT, null, null,
                0, 1, true, true, "secret",
                VALID_JSON
        );

        ElectionDefinitionService.ElectionDefinitionValidationResult result = service.validate(poll);
        assertTrue(result.valid());
    }
}
