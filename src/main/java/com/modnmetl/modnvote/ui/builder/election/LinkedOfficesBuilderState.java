package com.modnmetl.modnvote.ui.builder.election;

import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;
import com.modnmetl.modnvote.domain.election.OfficeDependencyRule;
import com.modnmetl.modnvote.domain.election.OfficeDependencyType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable, in-memory editing model for a linked-offices {@link ElectionDefinition}.
 *
 * This is the editing buffer behind the admin builder GUI. It is intentionally
 * <strong>Bukkit-free</strong> so it can be unit-tested without a server, and it
 * is <strong>not</strong> a source of truth: it is always loaded from an
 * {@link ElectionDefinition} and converted back into one via {@link #toDefinition()},
 * which is then serialized and saved through the service layer.
 *
 * Office membership is derived from candidate eligibility: a candidate that is
 * eligible for an office stands for that office. The builder therefore edits
 * candidate eligibility rather than a separate per-office candidate list, which
 * keeps the model generic and consistent (no office name is hardcoded).
 */
public final class LinkedOfficesBuilderState {

    private final Map<String, OfficeDraft> offices = new LinkedHashMap<>();
    private final Map<String, CandidateDraft> candidates = new LinkedHashMap<>();
    private final List<DependencyDraft> dependencies = new ArrayList<>();

    public static LinkedOfficesBuilderState empty() {
        return new LinkedOfficesBuilderState();
    }

    public static LinkedOfficesBuilderState fromDefinition(ElectionDefinition definition) {
        LinkedOfficesBuilderState state = new LinkedOfficesBuilderState();
        if (definition == null) {
            return state;
        }
        for (ContestDefinition contest : definition.contests()) {
            state.offices.put(contest.officeKey(), new OfficeDraft(
                    contest.officeKey(),
                    contest.displayName(),
                    contest.method(),
                    contest.seats() <= 0 ? 1 : contest.seats(),
                    contest.maxSelections(),
                    contest.allowAbstain()
            ));
        }
        for (CandidateDefinition candidate : definition.candidates()) {
            state.candidates.put(candidate.candidateKey(), new CandidateDraft(
                    candidate.candidateKey(),
                    candidate.displayName(),
                    new ArrayList<>(candidate.eligibleOfficeKeys())
            ));
        }
        for (OfficeDependencyRule rule : definition.dependencies()) {
            state.dependencies.add(new DependencyDraft(
                    rule.type(), rule.fromOfficeKey(), rule.appliesToOfficeKey()));
        }
        return state;
    }

    // --- Offices ------------------------------------------------------------

    /** Creates an office with sensible defaults if the key is new; otherwise no-op. */
    public OfficeDraft createOffice(String officeKey) {
        return offices.computeIfAbsent(officeKey,
                key -> new OfficeDraft(key, null, null, 1, null, false));
    }

    public OfficeDraft office(String officeKey) {
        return offices.get(officeKey);
    }

    public List<OfficeDraft> offices() {
        return new ArrayList<>(offices.values());
    }

    public boolean hasOffice(String officeKey) {
        return offices.containsKey(officeKey);
    }

    /**
     * Removes an office and cascades: removes dependencies that reference it and
     * strips it from every candidate's eligibility list, keeping the model
     * internally consistent.
     */
    public void removeOffice(String officeKey) {
        if (offices.remove(officeKey) == null) {
            return;
        }
        dependencies.removeIf(dep -> dep.fromOfficeKey().equals(officeKey)
                || dep.appliesToOfficeKey().equals(officeKey));
        for (CandidateDraft candidate : candidates.values()) {
            candidate.eligibleOfficeKeys().remove(officeKey);
        }
    }

    /** Renames an office key, preserving order and updating all references. */
    public void renameOffice(String oldKey, String newKey) {
        if (oldKey.equals(newKey) || !offices.containsKey(oldKey) || offices.containsKey(newKey)) {
            return;
        }
        Map<String, OfficeDraft> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, OfficeDraft> entry : offices.entrySet()) {
            if (entry.getKey().equals(oldKey)) {
                OfficeDraft draft = entry.getValue();
                draft.setOfficeKey(newKey);
                rebuilt.put(newKey, draft);
            } else {
                rebuilt.put(entry.getKey(), entry.getValue());
            }
        }
        offices.clear();
        offices.putAll(rebuilt);

        for (CandidateDraft candidate : candidates.values()) {
            List<String> eligible = candidate.eligibleOfficeKeys();
            for (int i = 0; i < eligible.size(); i++) {
                if (eligible.get(i).equals(oldKey)) {
                    eligible.set(i, newKey);
                }
            }
        }
        for (DependencyDraft dep : new ArrayList<>(dependencies)) {
            dep.remapOffice(oldKey, newKey);
        }
    }

    // --- Candidates ---------------------------------------------------------

    public CandidateDraft createCandidate(String candidateKey) {
        return candidates.computeIfAbsent(candidateKey,
                key -> new CandidateDraft(key, null, new ArrayList<>()));
    }

    public CandidateDraft candidate(String candidateKey) {
        return candidates.get(candidateKey);
    }

    public List<CandidateDraft> candidates() {
        return new ArrayList<>(candidates.values());
    }

    public boolean hasCandidate(String candidateKey) {
        return candidates.containsKey(candidateKey);
    }

    public void removeCandidate(String candidateKey) {
        candidates.remove(candidateKey);
    }

    /** Renames a candidate key, preserving insertion order. */
    public void renameCandidate(String oldKey, String newKey) {
        if (oldKey.equals(newKey) || !candidates.containsKey(oldKey) || candidates.containsKey(newKey)) {
            return;
        }
        Map<String, CandidateDraft> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, CandidateDraft> entry : candidates.entrySet()) {
            if (entry.getKey().equals(oldKey)) {
                CandidateDraft draft = entry.getValue();
                draft.setCandidateKey(newKey);
                rebuilt.put(newKey, draft);
            } else {
                rebuilt.put(entry.getKey(), entry.getValue());
            }
        }
        candidates.clear();
        candidates.putAll(rebuilt);
    }

    /** Toggles whether a candidate is eligible for (and therefore stands in) an office. */
    public void toggleEligibility(String candidateKey, String officeKey) {
        CandidateDraft candidate = candidates.get(candidateKey);
        if (candidate == null || !offices.containsKey(officeKey)) {
            return;
        }
        List<String> eligible = candidate.eligibleOfficeKeys();
        if (!eligible.remove(officeKey)) {
            eligible.add(officeKey);
        }
    }

    // --- Dependencies -------------------------------------------------------

    public List<DependencyDraft> dependencies() {
        return new ArrayList<>(dependencies);
    }

    /** Adds an EXCLUDE_WINNERS dependency if both offices exist and it is not a duplicate. */
    public void addExcludeWinnersDependency(String fromOfficeKey, String appliesToOfficeKey) {
        if (!offices.containsKey(fromOfficeKey) || !offices.containsKey(appliesToOfficeKey)) {
            return;
        }
        for (DependencyDraft dep : dependencies) {
            if (dep.type() == OfficeDependencyType.EXCLUDE_WINNERS
                    && dep.fromOfficeKey().equals(fromOfficeKey)
                    && dep.appliesToOfficeKey().equals(appliesToOfficeKey)) {
                return;
            }
        }
        dependencies.add(new DependencyDraft(
                OfficeDependencyType.EXCLUDE_WINNERS, fromOfficeKey, appliesToOfficeKey));
    }

    public void removeDependency(int index) {
        if (index >= 0 && index < dependencies.size()) {
            dependencies.remove(index);
        }
    }

    // --- Counts -------------------------------------------------------------

    public int officeCount() {
        return offices.size();
    }

    public int candidateCount() {
        return candidates.size();
    }

    public int dependencyCount() {
        return dependencies.size();
    }

    // --- Conversion ---------------------------------------------------------

    /**
     * Builds an {@link ElectionDefinition} from the current edit buffer. Each
     * office's candidate list is derived from candidate eligibility (candidates
     * eligible for an office stand in it), in candidate insertion order. For an
     * APPROVAL_TOP_N office whose {@code maxSelections} has not been set, it
     * defaults to the seat count so the definition can validate.
     */
    public ElectionDefinition toDefinition() {
        List<ContestDefinition> contests = new ArrayList<>();
        for (OfficeDraft office : offices.values()) {
            List<String> officeCandidates = new ArrayList<>();
            for (CandidateDraft candidate : candidates.values()) {
                if (candidate.eligibleOfficeKeys().contains(office.officeKey())) {
                    officeCandidates.add(candidate.candidateKey());
                }
            }

            Integer maxSelections = office.maxSelections();
            if (office.method() == CountingMethod.APPROVAL_TOP_N && maxSelections == null) {
                maxSelections = office.seats();
            }

            contests.add(new ContestDefinition(
                    office.officeKey(),
                    office.displayName(),
                    office.method(),
                    office.seats(),
                    maxSelections,
                    office.allowAbstain(),
                    officeCandidates
            ));
        }

        List<CandidateDefinition> candidateDefs = new ArrayList<>();
        for (CandidateDraft candidate : candidates.values()) {
            candidateDefs.add(new CandidateDefinition(
                    candidate.candidateKey(),
                    candidate.displayName(),
                    new ArrayList<>(candidate.eligibleOfficeKeys())
            ));
        }

        List<OfficeDependencyRule> rules = new ArrayList<>();
        for (DependencyDraft dep : dependencies) {
            rules.add(new OfficeDependencyRule(dep.type(), dep.fromOfficeKey(), dep.appliesToOfficeKey()));
        }

        return new ElectionDefinition(ElectionDefinition.LINKED_OFFICES_MODEL, contests, candidateDefs, rules);
    }

    /** Mutable office editing buffer. */
    public static final class OfficeDraft {
        private String officeKey;
        private String displayName;
        private CountingMethod method;
        private int seats;
        private Integer maxSelections;
        private boolean allowAbstain;

        OfficeDraft(String officeKey, String displayName, CountingMethod method,
                    int seats, Integer maxSelections, boolean allowAbstain) {
            this.officeKey = officeKey;
            this.displayName = displayName;
            this.method = method;
            this.seats = seats;
            this.maxSelections = maxSelections;
            this.allowAbstain = allowAbstain;
        }

        public String officeKey() {
            return officeKey;
        }

        void setOfficeKey(String officeKey) {
            this.officeKey = officeKey;
        }

        public String displayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public CountingMethod method() {
            return method;
        }

        /** Sets the counting method. IRV is single-seat, so seats is forced to 1. */
        public void setMethod(CountingMethod method) {
            this.method = method;
            if (method == CountingMethod.IRV) {
                this.seats = 1;
            }
        }

        public int seats() {
            return seats;
        }

        public void setSeats(int seats) {
            this.seats = Math.max(1, seats);
            if (method == CountingMethod.IRV) {
                this.seats = 1;
            }
        }

        public Integer maxSelections() {
            return maxSelections;
        }

        public void setMaxSelections(Integer maxSelections) {
            this.maxSelections = maxSelections;
        }

        public boolean allowAbstain() {
            return allowAbstain;
        }

        public void setAllowAbstain(boolean allowAbstain) {
            this.allowAbstain = allowAbstain;
        }
    }

    /** Mutable candidate editing buffer. */
    public static final class CandidateDraft {
        private String candidateKey;
        private String displayName;
        private final List<String> eligibleOfficeKeys;

        CandidateDraft(String candidateKey, String displayName, List<String> eligibleOfficeKeys) {
            this.candidateKey = candidateKey;
            this.displayName = displayName;
            this.eligibleOfficeKeys = eligibleOfficeKeys;
        }

        public String candidateKey() {
            return candidateKey;
        }

        void setCandidateKey(String candidateKey) {
            this.candidateKey = candidateKey;
        }

        public String displayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<String> eligibleOfficeKeys() {
            return eligibleOfficeKeys;
        }
    }

    /** Mutable dependency editing buffer. */
    public static final class DependencyDraft {
        private final OfficeDependencyType type;
        private String fromOfficeKey;
        private String appliesToOfficeKey;

        DependencyDraft(OfficeDependencyType type, String fromOfficeKey, String appliesToOfficeKey) {
            this.type = type;
            this.fromOfficeKey = fromOfficeKey;
            this.appliesToOfficeKey = appliesToOfficeKey;
        }

        public OfficeDependencyType type() {
            return type;
        }

        public String fromOfficeKey() {
            return fromOfficeKey;
        }

        public String appliesToOfficeKey() {
            return appliesToOfficeKey;
        }

        void remapOffice(String oldKey, String newKey) {
            if (fromOfficeKey.equals(oldKey)) {
                fromOfficeKey = newKey;
            }
            if (appliesToOfficeKey.equals(oldKey)) {
                appliesToOfficeKey = newKey;
            }
        }
    }
}
