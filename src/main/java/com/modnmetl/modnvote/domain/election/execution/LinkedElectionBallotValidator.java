package com.modnmetl.modnvote.domain.election.execution;

import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates a {@link LinkedElectionBallot} against its {@link ElectionDefinition}.
 *
 * <p>This validator never throws for ordinary voter mistakes. Every problem is
 * reported as a structured {@link BallotValidationIssue} in a deterministic
 * order, so a caller can present all problems at once or branch on the
 * {@link BallotValidationCode}. It validates ballot intent only; it does not
 * submit, store, or count anything.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>All contests: office must exist; the vote shape must match the contest's
 *       counting method; the voter must not respond to the same office twice;
 *       and the definition's dependency references must resolve.</li>
 *   <li>Ranked (IRV) contests: every candidate must exist, with no duplicates,
 *       and all must be eligible for the office.</li>
 *   <li>Approval (APPROVAL_TOP_N) contests: every candidate must exist, with no
 *       duplicates, must not exceed {@code maxSelections}, and all must be
 *       eligible for the office.</li>
 * </ul>
 */
public final class LinkedElectionBallotValidator {

    private final ElectionDependencyEvaluator dependencyEvaluator = new ElectionDependencyEvaluator();

    /**
     * Validates the ballot and returns all issues. Never throws for ordinary
     * validation failures.
     */
    public BallotValidationResult validate(LinkedElectionBallot ballot) {
        Objects.requireNonNull(ballot, "ballot");
        ElectionDefinition definition = ballot.electionDefinition();

        List<BallotValidationIssue> issues = new ArrayList<>();

        // Definition-level: dependency references must resolve.
        DependencyEvaluation dependencies = dependencyEvaluator.evaluateDependencies(definition);
        for (String unresolved : dependencies.unresolvedReferences()) {
            issues.add(new BallotValidationIssue(
                    BallotValidationCode.UNRESOLVED_DEPENDENCY,
                    null,
                    "dependency references unknown office '" + unresolved + "'."));
        }

        Set<String> respondedOffices = new LinkedHashSet<>();
        for (ContestVote vote : ballot.contestVotes()) {
            String officeKey = vote.officeKey();

            if (!respondedOffices.add(officeKey)) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.DUPLICATE_RESPONSE,
                        officeKey,
                        "office '" + officeKey + "' has more than one response on the ballot."));
                continue;
            }

            ContestDefinition contest = definition.findContest(officeKey).orElse(null);
            if (contest == null) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.UNKNOWN_OFFICE,
                        officeKey,
                        "response references unknown office '" + officeKey + "'."));
                continue;
            }

            validateResponse(definition, contest, vote, issues);
        }

        return new BallotValidationResult(issues);
    }

    private void validateResponse(ElectionDefinition definition,
                                  ContestDefinition contest,
                                  ContestVote vote,
                                  List<BallotValidationIssue> issues) {
        String officeKey = contest.officeKey();
        CountingMethod method = contest.method();

        if (vote instanceof RankedContestVote ranked) {
            if (method != CountingMethod.IRV) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.WRONG_VOTE_TYPE,
                        officeKey,
                        "office '" + officeKey + "' expects an approval response, not a ranked one."));
                return;
            }
            validateCandidateList(definition, contest, ranked.orderedCandidateKeys(), issues);
        } else if (vote instanceof ApprovalContestVote approval) {
            if (method != CountingMethod.APPROVAL_TOP_N) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.WRONG_VOTE_TYPE,
                        officeKey,
                        "office '" + officeKey + "' expects a ranked response, not an approval one."));
                return;
            }
            List<String> selected = approval.selectedCandidateKeys();
            Integer maxSelections = contest.maxSelections();
            if (maxSelections != null && selected.size() > maxSelections) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.EXCEEDS_MAX_SELECTIONS,
                        officeKey,
                        "office '" + officeKey + "' allows at most " + maxSelections
                                + " selections but " + selected.size() + " were made."));
            }
            validateCandidateList(definition, contest, selected, issues);
        }
    }

    /**
     * Checks that every candidate key in a response exists, is not duplicated
     * within the response, and is eligible for the contest's office.
     */
    private void validateCandidateList(ElectionDefinition definition,
                                       ContestDefinition contest,
                                       List<String> candidateKeys,
                                       List<BallotValidationIssue> issues) {
        String officeKey = contest.officeKey();
        Set<String> seen = new LinkedHashSet<>();

        for (String candidateKey : candidateKeys) {
            if (!seen.add(candidateKey)) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.DUPLICATE_CANDIDATE,
                        officeKey,
                        "candidate '" + candidateKey + "' appears more than once for office '" + officeKey + "'."));
                continue;
            }

            CandidateDefinition candidate = definition.findCandidate(candidateKey).orElse(null);
            if (candidate == null) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.UNKNOWN_CANDIDATE,
                        officeKey,
                        "response for office '" + officeKey + "' references unknown candidate '" + candidateKey + "'."));
                continue;
            }

            if (!candidate.eligibleOfficeKeys().contains(officeKey)) {
                issues.add(new BallotValidationIssue(
                        BallotValidationCode.INELIGIBLE_CANDIDATE,
                        officeKey,
                        "candidate '" + candidateKey + "' is not eligible for office '" + officeKey + "'."));
            }
        }
    }
}
