package com.modnmetl.modnvote.domain.election;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ElectionDefinitionParser}.
 *
 * The Mayor/Council JSON used here is an EXAMPLE only. The parser treats it as
 * fully generic offices/candidates/dependencies and hardcodes nothing.
 */
class ElectionDefinitionParserTest {

    private static final String EXAMPLE_JSON = """
            {
              "model": "LINKED_OFFICES",
              "offices": {
                "mayor": {
                  "displayName": "Mayor",
                  "seats": 1,
                  "method": "IRV",
                  "candidates": ["alice", "bob", "carol"]
                },
                "council": {
                  "displayName": "Council",
                  "seats": 4,
                  "method": "APPROVAL_TOP_N",
                  "maxSelections": 4,
                  "excludeWinnersFrom": ["mayor"],
                  "candidates": ["alice", "bob", "carol", "dave", "emma", "frank", "grace"]
                }
              },
              "candidateDefinitions": {
                "alice": { "displayName": "Alice", "eligibleFor": ["mayor", "council"] },
                "bob":   { "displayName": "Bob",   "eligibleFor": ["mayor", "council"] },
                "carol": { "displayName": "Carol", "eligibleFor": ["mayor", "council"] },
                "dave":  { "displayName": "Dave",  "eligibleFor": ["council"] },
                "emma":  { "displayName": "Emma",  "eligibleFor": ["council"] },
                "frank": { "displayName": "Frank", "eligibleFor": ["council"] },
                "grace": { "displayName": "Grace", "eligibleFor": ["council"] }
              }
            }
            """;

    private final ElectionDefinitionParser parser = new ElectionDefinitionParser();

    @Test
    void parsesFullExampleIntoGenericModel() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);

        assertEquals("LINKED_OFFICES", definition.model());
        assertEquals(2, definition.contests().size());
        assertEquals(7, definition.candidates().size());
        assertEquals(1, definition.dependencies().size());
    }

    @Test
    void preservesOfficeOrder() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);

        List<String> officeKeys = definition.contests().stream().map(ContestDefinition::officeKey).toList();
        assertEquals(List.of("mayor", "council"), officeKeys);
    }

    @Test
    void preservesCandidateOrder() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);

        List<String> candidateKeys = definition.candidates().stream()
                .map(CandidateDefinition::candidateKey).toList();
        assertEquals(List.of("alice", "bob", "carol", "dave", "emma", "frank", "grace"), candidateKeys);
    }

    @Test
    void parsesContestFieldsGenerically() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);

        ContestDefinition mayor = definition.findContest("mayor").orElseThrow();
        assertEquals(CountingMethod.IRV, mayor.method());
        assertEquals(1, mayor.seats());
        assertNull(mayor.maxSelections());
        assertFalse(mayor.allowAbstain());
        assertEquals(List.of("alice", "bob", "carol"), mayor.candidateKeys());

        ContestDefinition council = definition.findContest("council").orElseThrow();
        assertEquals(CountingMethod.APPROVAL_TOP_N, council.method());
        assertEquals(4, council.seats());
        assertEquals(Integer.valueOf(4), council.maxSelections());
        assertEquals(7, council.candidateKeys().size());
    }

    @Test
    void convertsExcludeWinnersFromIntoExcludeWinnersDependency() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);

        assertEquals(1, definition.dependencies().size());
        OfficeDependencyRule rule = definition.dependencies().get(0);
        assertEquals(OfficeDependencyType.EXCLUDE_WINNERS, rule.type());
        assertEquals("mayor", rule.fromOfficeKey());
        assertEquals("council", rule.appliesToOfficeKey());
    }

    @Test
    void defaultsAllowAbstainToFalse() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);
        assertFalse(definition.findContest("council").orElseThrow().allowAbstain());
    }

    @Test
    void parsesCandidateEligibilityGenerically() {
        ElectionDefinition definition = parser.parse(EXAMPLE_JSON);

        assertEquals(List.of("mayor", "council"),
                definition.findCandidate("alice").orElseThrow().eligibleOfficeKeys());
        assertEquals(List.of("council"),
                definition.findCandidate("dave").orElseThrow().eligibleOfficeKeys());
    }

    @Test
    void parsesStvMethodForMultiSeatContest() {
        String json = EXAMPLE_JSON
                .replace("\"method\": \"APPROVAL_TOP_N\",", "\"method\": \"STV\",")
                .replace("\"maxSelections\": 4,", "");
        ElectionDefinition definition = parser.parse(json);

        ContestDefinition council = definition.findContest("council").orElseThrow();
        assertEquals(CountingMethod.STV, council.method());
        assertEquals(4, council.seats());
        assertNull(council.maxSelections());
    }

    @Test
    void rejectsUnknownModel() {
        String json = EXAMPLE_JSON.replace("\"LINKED_OFFICES\"", "\"SOMETHING_ELSE\"");
        ElectionDefinitionException ex = assertThrows(ElectionDefinitionException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().toLowerCase().contains("model"));
    }

    @Test
    void rejectsUnknownCountingMethod() {
        String json = EXAMPLE_JSON.replace("\"IRV\"", "\"BORDA_COUNT\"");
        ElectionDefinitionException ex = assertThrows(ElectionDefinitionException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().toLowerCase().contains("method"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ElectionDefinitionException.class, () -> parser.parse("{ not valid json"));
    }
}
