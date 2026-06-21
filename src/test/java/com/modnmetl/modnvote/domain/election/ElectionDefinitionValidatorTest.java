package com.modnmetl.modnvote.domain.election;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ElectionDefinitionValidator}.
 *
 * Mayor/Council keys are EXAMPLE data only; validation is fully generic.
 */
class ElectionDefinitionValidatorTest {

    private final ElectionDefinitionParser parser = new ElectionDefinitionParser();
    private final ElectionDefinitionValidator validator = new ElectionDefinitionValidator();

    private static final String VALID_EXAMPLE_JSON = """
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

    @Test
    void acceptsValidExample() {
        ElectionDefinition definition = parser.parse(VALID_EXAMPLE_JSON);
        assertTrue(validator.findIssues(definition).isEmpty());
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void rejectsDuplicateOfficeKeys() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(
                        new ContestDefinition("mayor", "Mayor", CountingMethod.IRV, 1, null, false, List.of("alice")),
                        new ContestDefinition("mayor", "Mayor Two", CountingMethod.IRV, 1, null, false, List.of("alice"))
                ),
                List.of(new CandidateDefinition("alice", "Alice", List.of("mayor"))),
                List.of()
        );
        assertContainsIssue(definition, "duplicate office");
        assertThrows(ElectionDefinitionException.class, () -> validator.validate(definition));
    }

    @Test
    void rejectsDuplicateCandidateKeys() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("mayor", "Mayor", CountingMethod.IRV, 1, null, false, List.of("alice"))),
                List.of(
                        new CandidateDefinition("alice", "Alice", List.of("mayor")),
                        new CandidateDefinition("alice", "Alice Two", List.of("mayor"))
                ),
                List.of()
        );
        assertContainsIssue(definition, "duplicate candidate");
    }

    @Test
    void rejectsCandidateListedForOfficeWithoutEligibility() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("mayor", "Mayor", CountingMethod.IRV, 1, null, false, List.of("dave"))),
                // dave is not eligible for mayor
                List.of(new CandidateDefinition("dave", "Dave", List.of())),
                List.of()
        );
        assertContainsIssue(definition, "not eligible");
    }

    @Test
    void rejectsDependencyReferencingMissingOffice() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("mayor", "Mayor", CountingMethod.IRV, 1, null, false, List.of("alice"))),
                List.of(new CandidateDefinition("alice", "Alice", List.of("mayor"))),
                List.of(new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, "ghost", "mayor"))
        );
        assertContainsIssue(definition, "unknown office 'ghost'");
    }

    @Test
    void rejectsDependencyCycle() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(
                        new ContestDefinition("a", "Office A", CountingMethod.IRV, 1, null, false, List.of("alice")),
                        new ContestDefinition("b", "Office B", CountingMethod.IRV, 1, null, false, List.of("bob"))
                ),
                List.of(
                        new CandidateDefinition("alice", "Alice", List.of("a")),
                        new CandidateDefinition("bob", "Bob", List.of("b"))
                ),
                List.of(
                        new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, "a", "b"),
                        new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, "b", "a")
                )
        );
        assertContainsIssue(definition, "cycle");
    }

    @Test
    void rejectsIrvWithMoreThanOneSeat() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("mayor", "Mayor", CountingMethod.IRV, 2, null, false, List.of("alice", "bob"))),
                List.of(
                        new CandidateDefinition("alice", "Alice", List.of("mayor")),
                        new CandidateDefinition("bob", "Bob", List.of("mayor"))
                ),
                List.of()
        );
        assertContainsIssue(definition, "exactly 1 seat");
    }

    @Test
    void rejectsApprovalWithMaxSelectionsBelowOne() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("council", "Council", CountingMethod.APPROVAL_TOP_N, 1, 0, false, List.of("alice"))),
                List.of(new CandidateDefinition("alice", "Alice", List.of("council"))),
                List.of()
        );
        assertContainsIssue(definition, "maxSelections >= 1");
    }

    @Test
    void acceptsStvWithMultipleSeats() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("council", "Council", CountingMethod.STV, 4, null, false,
                        List.of("alice", "bob", "carol", "dave", "emma"))),
                List.of(
                        new CandidateDefinition("alice", "Alice", List.of("council")),
                        new CandidateDefinition("bob", "Bob", List.of("council")),
                        new CandidateDefinition("carol", "Carol", List.of("council")),
                        new CandidateDefinition("dave", "Dave", List.of("council")),
                        new CandidateDefinition("emma", "Emma", List.of("council"))),
                List.of());
        assertTrue(validator.findIssues(definition).isEmpty(),
                () -> "expected a valid STV definition, found: " + validator.findIssues(definition));
        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void rejectsStvWithZeroSeats() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("council", "Council", CountingMethod.STV, 0, null, false,
                        List.of("alice", "bob"))),
                List.of(
                        new CandidateDefinition("alice", "Alice", List.of("council")),
                        new CandidateDefinition("bob", "Bob", List.of("council"))),
                List.of());
        assertContainsIssue(definition, "seats >= 1");
    }

    @Test
    void rejectsStvWithMaxSelections() {
        // STV uses ranked ballots; maxSelections is not applicable and is rejected.
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("council", "Council", CountingMethod.STV, 2, 3, false,
                        List.of("alice", "bob", "carol"))),
                List.of(
                        new CandidateDefinition("alice", "Alice", List.of("council")),
                        new CandidateDefinition("bob", "Bob", List.of("council")),
                        new CandidateDefinition("carol", "Carol", List.of("council"))),
                List.of());
        assertContainsIssue(definition, "must not set maxSelections");
    }

    @Test
    void rejectsContestWithFewerEligibleCandidatesThanSeats() {
        ElectionDefinition definition = new ElectionDefinition(
                "LINKED_OFFICES",
                List.of(new ContestDefinition("council", "Council", CountingMethod.APPROVAL_TOP_N, 4, 4, false, List.of("alice"))),
                List.of(new CandidateDefinition("alice", "Alice", List.of("council"))),
                List.of()
        );
        assertContainsIssue(definition, "fewer eligible candidates");
    }

    private void assertContainsIssue(ElectionDefinition definition, String fragment) {
        List<String> issues = validator.findIssues(definition);
        assertTrue(issues.stream().anyMatch(issue -> issue.contains(fragment)),
                "Expected an issue containing '" + fragment + "' but found: " + issues);
    }
}
