package com.modnmetl.modnvote.service;

import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;
import com.modnmetl.modnvote.domain.election.OfficeDependencyType;

import java.util.List;

/**
 * Reusable Mayor/Council fixture for linked-offices storage tests. Mirrors the
 * execution-model fixture (which is package-private to another package): Mayor is
 * IRV (1 seat), Council is APPROVAL_TOP_N (3 seats, maxSelections 3), with an
 * EXCLUDE_WINNERS dependency Mayor -> Council. Names are illustrative only.
 */
final class LinkedStorageTestFixtures {

    static final String MAYOR = "mayor";
    static final String COUNCIL = "council";

    static final String ALICE = "alice";
    static final String BOB = "bob";
    static final String CAROL = "carol";
    static final String DAVE = "dave";
    static final String ERIN = "erin";
    static final String FRANK = "frank";
    static final String GRACE = "grace";

    private LinkedStorageTestFixtures() {
    }

    static ElectionDefinition mayorCouncil() {
        ContestDefinition mayor = new ContestDefinition(
                MAYOR, "Mayor", CountingMethod.IRV, 1, null, false,
                List.of(ALICE, BOB, CAROL));
        ContestDefinition council = new ContestDefinition(
                COUNCIL, "Council", CountingMethod.APPROVAL_TOP_N, 3, 3, false,
                List.of(ALICE, DAVE, ERIN, FRANK, GRACE));

        List<CandidateDefinition> candidates = List.of(
                new CandidateDefinition(ALICE, "Alice", List.of(MAYOR, COUNCIL)),
                new CandidateDefinition(BOB, "Bob", List.of(MAYOR)),
                new CandidateDefinition(CAROL, "Carol", List.of(MAYOR)),
                new CandidateDefinition(DAVE, "Dave", List.of(COUNCIL)),
                new CandidateDefinition(ERIN, "Erin", List.of(COUNCIL)),
                new CandidateDefinition(FRANK, "Frank", List.of(COUNCIL)),
                new CandidateDefinition(GRACE, "Grace", List.of(COUNCIL)));

        List<OfficeDependencyRule> dependencies = List.of(
                new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, MAYOR, COUNCIL));

        return new ElectionDefinition(
                ElectionDefinition.LINKED_OFFICES_MODEL, List.of(mayor, council), candidates, dependencies);
    }

    /**
     * Builds a DRAFT LINKED_OFFICES poll domain object. (Storage is a primitive,
     * not a real submission, so status is irrelevant — DRAFT keeps it clearly
     * non-votable.)
     */
    static Poll linkedOfficesPoll(long pollId, String slug) {
        return new Poll(
                pollId,
                slug,
                "Linked Offices Poll",
                "Description",
                PollType.LINKED_OFFICES,
                PollStatus.DRAFT,
                null,
                null,
                1,
                3,
                false,
                true,
                "participation-secret-" + pollId
        );
    }
}
