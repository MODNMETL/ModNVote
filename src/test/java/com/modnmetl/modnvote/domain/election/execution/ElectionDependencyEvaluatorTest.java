package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;
import com.modnmetl.modnvote.domain.election.OfficeDependencyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.ALICE;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.BOB;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.CAROL;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.COUNCIL;
import static com.modnmetl.modnvote.domain.election.execution.LinkedElectionFixtures.MAYOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectionDependencyEvaluatorTest {

    private final ElectionDependencyEvaluator evaluator = new ElectionDependencyEvaluator();

    @Test
    void resolvesDependenciesAndCountsSourceFirst() {
        DependencyEvaluation evaluation = evaluator.evaluateDependencies(
                LinkedElectionFixtures.mayorCouncil());

        assertTrue(evaluation.allReferencesResolve());
        assertFalse(evaluation.hasCycle());
        // Council depends on Mayor (EXCLUDE_WINNERS Mayor -> Council).
        assertEquals(List.of(MAYOR), evaluation.precedingOfficesByOffice().get(COUNCIL));
        assertTrue(evaluation.precedingOfficesByOffice().get(MAYOR).isEmpty());
        // Mayor must be counted before Council.
        assertEquals(List.of(MAYOR, COUNCIL), evaluation.evaluationOrder());
    }

    @Test
    void handlesMissingReferences() {
        ElectionDefinition base = LinkedElectionFixtures.mayorCouncil();
        ElectionDefinition broken = new ElectionDefinition(
                base.model(),
                base.contests(),
                base.candidates(),
                List.of(new OfficeDependencyRule(OfficeDependencyType.EXCLUDE_WINNERS, MAYOR, "ghost")));

        DependencyEvaluation evaluation = evaluator.evaluateDependencies(broken);

        assertFalse(evaluation.allReferencesResolve());
        assertEquals(List.of("ghost"), evaluation.unresolvedReferences());
    }

    @Test
    void producesDeterministicOutput() {
        ElectionDefinition definition = LinkedElectionFixtures.mayorCouncil();

        DependencyEvaluation first = evaluator.evaluateDependencies(definition);
        DependencyEvaluation second = evaluator.evaluateDependencies(definition);

        assertEquals(first.evaluationOrder(), second.evaluationOrder());
        assertEquals(first.precedingOfficesByOffice(), second.precedingOfficesByOffice());
        assertEquals(first.unresolvedReferences(), second.unresolvedReferences());
    }

    @Test
    void determinesEligibleCandidatesInContestOrder() {
        ElectionDefinition definition = LinkedElectionFixtures.mayorCouncil();

        assertEquals(List.of(ALICE, BOB, CAROL),
                evaluator.determineCandidatesEligibleForContest(definition, MAYOR));
    }

    @Test
    void unknownOfficeHasNoEligibleCandidates() {
        ElectionDefinition definition = LinkedElectionFixtures.mayorCouncil();

        assertTrue(evaluator.determineCandidatesEligibleForContest(definition, "treasurer").isEmpty());
    }
}
