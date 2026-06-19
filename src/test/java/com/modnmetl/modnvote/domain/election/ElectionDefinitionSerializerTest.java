package com.modnmetl.modnvote.domain.election;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ElectionDefinitionSerializer}: round-trip stability with the
 * parser, deterministic ordering, and dependency serialization.
 */
class ElectionDefinitionSerializerTest {

    private static final String JSON = """
            {
              "model": "LINKED_OFFICES",
              "offices": {
                "mayor": {"displayName": "Mayor", "method": "IRV", "seats": 1, "allowAbstain": true,
                          "candidates": ["alice", "bob", "carol"]},
                "council": {"displayName": "Council", "method": "APPROVAL_TOP_N", "seats": 2, "maxSelections": 3,
                            "candidates": ["alice", "bob", "carol", "dave"], "excludeWinnersFrom": ["mayor"]}
              },
              "candidateDefinitions": {
                "alice": {"displayName": "Alice", "eligibleFor": ["mayor", "council"]},
                "bob":   {"displayName": "Bob",   "eligibleFor": ["mayor", "council"]},
                "carol": {"displayName": "Carol", "eligibleFor": ["mayor", "council"]},
                "dave":  {"displayName": "Dave",  "eligibleFor": ["council"]}
              }
            }
            """;

    private final ElectionDefinitionParser parser = new ElectionDefinitionParser();
    private final ElectionDefinitionSerializer serializer = new ElectionDefinitionSerializer();

    @Test
    void roundTripsThroughParser() {
        ElectionDefinition original = parser.parse(JSON);
        ElectionDefinition reparsed = parser.parse(serializer.serialize(original));
        assertEquals(original, reparsed, "parse(serialize(x)) must equal x");
    }

    @Test
    void serializationIsDeterministic() {
        ElectionDefinition definition = parser.parse(JSON);
        assertEquals(serializer.serialize(definition), serializer.serialize(definition));
    }

    @Test
    void preservesOfficeAndCandidateOrder() {
        ElectionDefinition definition = parser.parse(JSON);
        ElectionDefinition reparsed = parser.parse(serializer.serialize(definition));

        assertEquals(List.of("mayor", "council"),
                reparsed.contests().stream().map(ContestDefinition::officeKey).toList());
        assertEquals(List.of("alice", "bob", "carol", "dave"),
                reparsed.candidates().stream().map(CandidateDefinition::candidateKey).toList());
    }

    @Test
    void serializesExcludeWinnersDependencies() {
        ElectionDefinition definition = parser.parse(JSON);
        String json = serializer.serialize(definition);

        assertTrue(json.contains("excludeWinnersFrom"), "Serialized JSON should carry excludeWinnersFrom.");

        ElectionDefinition reparsed = parser.parse(json);
        assertEquals(1, reparsed.dependencies().size());
        OfficeDependencyRule rule = reparsed.dependencies().get(0);
        assertEquals(OfficeDependencyType.EXCLUDE_WINNERS, rule.type());
        assertEquals("mayor", rule.fromOfficeKey());
        assertEquals("council", rule.appliesToOfficeKey());
    }

    @Test
    void roundTripsAnEmptyDefinition() {
        ElectionDefinition empty = new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(), List.of(), List.of());
        ElectionDefinition reparsed = parser.parse(serializer.serialize(empty));
        assertEquals(empty, reparsed);
    }
}
