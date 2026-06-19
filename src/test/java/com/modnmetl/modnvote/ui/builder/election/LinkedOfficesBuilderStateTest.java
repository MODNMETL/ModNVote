package com.modnmetl.modnvote.ui.builder.election;

import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.ElectionDefinitionValidator;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState.OfficeDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Bukkit-free builder edit buffer: office/candidate create+delete,
 * derived office membership, dependency handling, and validation state.
 */
class LinkedOfficesBuilderStateTest {

    private final ElectionDefinitionValidator validator = new ElectionDefinitionValidator();

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
    void createOfficeAddsOne() {
        LinkedOfficesBuilderState state = LinkedOfficesBuilderState.empty();
        state.createOffice("mayor");
        assertEquals(1, state.officeCount());
        assertTrue(state.hasOffice("mayor"));
    }

    @Test
    void deleteOfficeRemovesItAndCascades() {
        LinkedOfficesBuilderState state = validState();
        assertTrue(state.candidate("alice").eligibleOfficeKeys().contains("mayor"));

        state.removeOffice("mayor");
        assertEquals(0, state.officeCount());
        assertFalse(state.candidate("alice").eligibleOfficeKeys().contains("mayor"),
                "Deleting an office must strip it from candidate eligibility.");
    }

    @Test
    void createCandidateAddsOne() {
        LinkedOfficesBuilderState state = LinkedOfficesBuilderState.empty();
        state.createCandidate("alice");
        assertEquals(1, state.candidateCount());
        assertTrue(state.hasCandidate("alice"));
    }

    @Test
    void deleteCandidateRemovesIt() {
        LinkedOfficesBuilderState state = validState();
        state.removeCandidate("bob");
        assertEquals(1, state.candidateCount());
        assertFalse(state.hasCandidate("bob"));
    }

    @Test
    void derivesOfficeMembershipFromEligibility() {
        LinkedOfficesBuilderState state = validState();
        ElectionDefinition definition = state.toDefinition();
        ContestDefinition mayor = definition.findContest("mayor").orElseThrow();
        assertEquals(List.of("alice", "bob"), mayor.candidateKeys());
    }

    @Test
    void addAndRemoveDependency() {
        LinkedOfficesBuilderState state = validState();
        state.createOffice("council").setMethod(CountingMethod.APPROVAL_TOP_N);

        state.addExcludeWinnersDependency("mayor", "council");
        assertEquals(1, state.dependencyCount());

        // Duplicate is ignored.
        state.addExcludeWinnersDependency("mayor", "council");
        assertEquals(1, state.dependencyCount());

        state.removeDependency(0);
        assertEquals(0, state.dependencyCount());
    }

    @Test
    void validStateValidatesAndBrokenStateDoesNot() {
        LinkedOfficesBuilderState state = validState();
        assertTrue(validator.findIssues(state.toDefinition()).isEmpty(),
                "Issues: " + validator.findIssues(state.toDefinition()));

        // Removing all candidates leaves the office with fewer eligible candidates than seats.
        state.removeCandidate("alice");
        state.removeCandidate("bob");
        assertFalse(validator.findIssues(state.toDefinition()).isEmpty());
    }
}
