package com.modnmetl.modnvote.ui.session.election;

import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.execution.ApprovalContestVote;
import com.modnmetl.modnvote.domain.election.execution.BallotValidationResult;
import com.modnmetl.modnvote.domain.election.execution.ContestVote;
import com.modnmetl.modnvote.domain.election.execution.ElectionDependencyEvaluator;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallot;
import com.modnmetl.modnvote.domain.election.execution.LinkedElectionBallotValidator;
import com.modnmetl.modnvote.domain.election.execution.RankedContestVote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bukkit-free, in-memory model of a voter's in-progress linked-offices ballot.
 *
 * <p>This is the testable heart of the linked-offices voting GUI. It holds the
 * voter's per-office selections, enforces per-office selection rules as the voter
 * edits, reports completion/validation status, and builds the immutable
 * {@link LinkedElectionBallot} that is validated and submitted. It contains no
 * Bukkit types so all state transitions can be unit-tested directly.
 *
 * <p>Per office:
 * <ul>
 *   <li>Ranked contests (IRV single-seat and STV multi-seat) keep an ordered
 *       ranking — toggling a candidate appends it to the ranking or removes it;
 *       remaining candidates keep their order. STV uses the same ranked screen as
 *       IRV with no selection cap.</li>
 *   <li>APPROVAL_TOP_N contests keep an ordered selection set bounded by the
 *       contest's {@code maxSelections}; toggling adds (while under the cap) or
 *       removes a candidate.</li>
 * </ul>
 *
 * <p>Dependency outcomes (e.g. {@code EXCLUDE_WINNERS}) are deliberately
 * <strong>not</strong> applied here: every candidate structurally eligible for an
 * office is offered to the voter. Excluding the eventual winner of a source
 * office from a dependent office happens only at count time, never at cast time.
 */
public final class LinkedOfficesVoteState {

    private final ElectionDefinition definition;
    private final ElectionDependencyEvaluator dependencyEvaluator = new ElectionDependencyEvaluator();
    private final LinkedElectionBallotValidator ballotValidator = new LinkedElectionBallotValidator();

    /** Office key -> ordered selections (rank order for IRV, selection order for approval). */
    private final Map<String, LinkedHashSet<String>> selectionsByOffice = new LinkedHashMap<>();

    public LinkedOfficesVoteState(ElectionDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        for (ContestDefinition contest : definition.contests()) {
            selectionsByOffice.putIfAbsent(contest.officeKey(), new LinkedHashSet<>());
        }
    }

    public ElectionDefinition definition() {
        return definition;
    }

    public List<ContestDefinition> contests() {
        return definition.contests();
    }

    public ContestDefinition requireContest(String officeKey) {
        return definition.findContest(officeKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown office '" + officeKey + "'."));
    }

    public boolean isRanked(String officeKey) {
        CountingMethod method = requireContest(officeKey).method();
        return method != null && method.usesRankedBallot();
    }

    public boolean isApproval(String officeKey) {
        return requireContest(officeKey).method() == CountingMethod.APPROVAL_TOP_N;
    }

    /**
     * Candidates the voter may pick for this office, in contest order. This is the
     * full structural eligibility list — dependency winners are not hidden.
     */
    public List<String> eligibleCandidates(String officeKey) {
        return dependencyEvaluator.determineCandidatesEligibleForContest(definition, officeKey);
    }

    /** Current selections for an office in voter order (rank order for IRV). */
    public List<String> selectionsFor(String officeKey) {
        return List.copyOf(requireSelections(officeKey));
    }

    public boolean isSelected(String officeKey, String candidateKey) {
        return requireSelections(officeKey).contains(candidateKey);
    }

    /**
     * @return the 1-based rank of a candidate within a ranked office, or -1 if not
     * ranked. Meaningful for IRV offices; for approval offices it reflects
     * selection order.
     */
    public int rankOf(String officeKey, String candidateKey) {
        int position = 1;
        for (String key : requireSelections(officeKey)) {
            if (key.equals(candidateKey)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    public int selectionCount(String officeKey) {
        return requireSelections(officeKey).size();
    }

    /**
     * Returns the per-office selection cap, or {@code null} if unbounded. IRV
     * offices are unbounded (the voter may rank every candidate); approval offices
     * use the contest's {@code maxSelections}.
     */
    public Integer maxSelections(String officeKey) {
        ContestDefinition contest = requireContest(officeKey);
        return contest.method() == CountingMethod.APPROVAL_TOP_N ? contest.maxSelections() : null;
    }

    /**
     * Toggles a candidate for an office.
     *
     * <ul>
     *   <li>If already selected, it is removed (ranked offices keep the order of
     *       the remaining candidates).</li>
     *   <li>If not selected, it is appended — unless an approval office is already
     *       at its {@code maxSelections} cap, in which case nothing changes.</li>
     * </ul>
     *
     * @return {@code true} if the selection set changed
     * @throws IllegalArgumentException if the office is unknown or the candidate is
     *                                  not eligible for it
     */
    public boolean toggle(String officeKey, String candidateKey) {
        Objects.requireNonNull(candidateKey, "candidateKey");
        requireContest(officeKey);
        if (!eligibleCandidates(officeKey).contains(candidateKey)) {
            throw new IllegalArgumentException(
                    "Candidate '" + candidateKey + "' is not eligible for office '" + officeKey + "'.");
        }

        LinkedHashSet<String> selections = requireSelections(officeKey);
        if (selections.contains(candidateKey)) {
            selections.remove(candidateKey);
            return true;
        }

        Integer max = maxSelections(officeKey);
        if (max != null && selections.size() >= max) {
            return false;
        }
        selections.add(candidateKey);
        return true;
    }

    /** Clears all selections for an office. */
    public boolean clearOffice(String officeKey) {
        LinkedHashSet<String> selections = requireSelections(officeKey);
        if (selections.isEmpty()) {
            return false;
        }
        selections.clear();
        return true;
    }

    public boolean hasResponse(String officeKey) {
        return !requireSelections(officeKey).isEmpty();
    }

    public boolean hasAnyResponse() {
        return selectionsByOffice.values().stream().anyMatch(set -> !set.isEmpty());
    }

    /**
     * An office counts as addressed for completion purposes when it has at least
     * one selection, or when the contest permits abstaining. Used for the overview
     * completion indicators.
     */
    public boolean officeAddressed(String officeKey) {
        return hasResponse(officeKey) || requireContest(officeKey).allowAbstain();
    }

    /**
     * Builds the immutable ballot from current selections. Offices with no
     * selection are omitted (an abstention); responses are emitted in definition
     * office order.
     */
    public LinkedElectionBallot buildBallot() {
        List<ContestVote> votes = new ArrayList<>();
        for (ContestDefinition contest : definition.contests()) {
            List<String> selections = List.copyOf(requireSelections(contest.officeKey()));
            if (selections.isEmpty()) {
                continue;
            }
            if (contest.method() != null && contest.method().usesRankedBallot()) {
                votes.add(new RankedContestVote(contest.officeKey(), selections));
            } else {
                votes.add(new ApprovalContestVote(contest.officeKey(), selections));
            }
        }
        return new LinkedElectionBallot(definition, votes);
    }

    public BallotValidationResult validate() {
        return ballotValidator.validate(buildBallot());
    }

    /**
     * A ballot is submittable when at least one office has a response, every office
     * that does not permit abstaining has a response, and the constructed ballot
     * passes validation.
     */
    public boolean isSubmittable() {
        if (!hasAnyResponse()) {
            return false;
        }
        for (ContestDefinition contest : definition.contests()) {
            if (!contest.allowAbstain() && !hasResponse(contest.officeKey())) {
                return false;
            }
        }
        return validate().valid();
    }

    private LinkedHashSet<String> requireSelections(String officeKey) {
        LinkedHashSet<String> selections = selectionsByOffice.get(officeKey);
        if (selections == null) {
            throw new IllegalArgumentException("Unknown office '" + officeKey + "'.");
        }
        return selections;
    }
}
