package com.modnmetl.modnvote.ui.builder.election;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-admin session state for the linked-offices builder GUI.
 *
 * Bukkit-free: it holds the editing {@link LinkedOfficesBuilderState}, the
 * current screen, navigation/edit context, and a slot→action map that the
 * renderer fills in and the click listener reads. It performs no persistence
 * and is not a source of truth; saves always flow back through the service layer.
 */
public final class LinkedOfficesBuilderSession {

    /** GUI screens in the builder flow. */
    public enum Screen {
        MAIN,
        OFFICES,
        OFFICE_EDITOR,
        CANDIDATES,
        CANDIDATE_EDITOR,
        DEPENDENCIES
    }

    /**
     * A click action bound to an inventory slot by the renderer.
     *
     * @param type a stable action identifier
     * @param arg  an optional argument (office key, candidate key, index, ...)
     */
    public record Action(String type, String arg) {
        public Action(String type) {
            this(type, null);
        }
    }

    private final UUID adminId;
    private final long pollId;
    private final LinkedOfficesBuilderState state;

    private Screen currentScreen = Screen.MAIN;
    private String editingOfficeKey;
    private String editingCandidateKey;
    private boolean addingDependency;
    private String pendingDependencyFrom;

    private boolean hasValidated;
    private boolean lastValidationValid;
    private List<String> lastValidationIssues = List.of();

    private final Map<Integer, Action> slotActions = new HashMap<>();

    public LinkedOfficesBuilderSession(UUID adminId, long pollId, LinkedOfficesBuilderState state) {
        this.adminId = adminId;
        this.pollId = pollId;
        this.state = state;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public long getPollId() {
        return pollId;
    }

    public LinkedOfficesBuilderState getState() {
        return state;
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }

    public void setCurrentScreen(Screen currentScreen) {
        this.currentScreen = currentScreen;
    }

    public String getEditingOfficeKey() {
        return editingOfficeKey;
    }

    public void setEditingOfficeKey(String editingOfficeKey) {
        this.editingOfficeKey = editingOfficeKey;
    }

    public String getEditingCandidateKey() {
        return editingCandidateKey;
    }

    public void setEditingCandidateKey(String editingCandidateKey) {
        this.editingCandidateKey = editingCandidateKey;
    }

    public boolean isAddingDependency() {
        return addingDependency;
    }

    public String getPendingDependencyFrom() {
        return pendingDependencyFrom;
    }

    public void beginAddDependency() {
        this.addingDependency = true;
        this.pendingDependencyFrom = null;
    }

    public void setPendingDependencyFrom(String pendingDependencyFrom) {
        this.pendingDependencyFrom = pendingDependencyFrom;
    }

    public void cancelAddDependency() {
        this.addingDependency = false;
        this.pendingDependencyFrom = null;
    }

    public boolean hasValidated() {
        return hasValidated;
    }

    public boolean isLastValidationValid() {
        return lastValidationValid;
    }

    public List<String> getLastValidationIssues() {
        return lastValidationIssues;
    }

    public void recordValidation(boolean valid, List<String> issues) {
        this.hasValidated = true;
        this.lastValidationValid = valid;
        this.lastValidationIssues = List.copyOf(issues);
    }

    public void clearValidation() {
        this.hasValidated = false;
        this.lastValidationValid = false;
        this.lastValidationIssues = List.of();
    }

    public void clearSlotActions() {
        slotActions.clear();
    }

    public void bindAction(int slot, Action action) {
        slotActions.put(slot, action);
    }

    public Action actionAt(int slot) {
        return slotActions.get(slot);
    }
}
