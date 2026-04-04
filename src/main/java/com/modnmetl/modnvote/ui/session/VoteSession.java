package com.modnmetl.modnvote.ui.session;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory session model for one player's active ranked-vote interaction.
 *
 * This class is part of the UI/session layer only.
 *
 * Responsibilities:
 * - track the player and poll bound to the session
 * - maintain temporary ranked selection state
 * - provide renderer-friendly access to option/rank state
 * - provide structured ranked-selection data for renderers and formatters
 * - expose lightweight UI/session validity checks
 *
 * Non-responsibilities:
 * - no database writes
 * - no anonymous ballot persistence
 * - no duplicate-prevention checks
 * - no authoritative ballot validation
 *
 * Final validation and commit remain the responsibility of the service layer.
 */
public final class VoteSession {

    private final UUID playerUuid;
    private final Poll poll;
    private final List<PollOption> options;
    private final Map<Long, PollOption> optionsById;
    private final Instant createdAt;

    private VoteScreen currentScreen;
    private Instant lastInteractionAt;
    private final List<Long> rankedOptionIds;

    public VoteSession(UUID playerUuid,
                       Poll poll,
                       List<PollOption> options) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.poll = Objects.requireNonNull(poll, "poll");
        this.options = validateAndCopyOptions(options);
        this.optionsById = buildOptionsById(this.options);
        this.createdAt = Instant.now();
        this.lastInteractionAt = this.createdAt;
        this.currentScreen = VoteScreen.SELECTION;
        this.rankedOptionIds = new ArrayList<>();

        if (poll.pollType() != PollType.RANKED_SINGLE_WINNER) {
            throw new IllegalArgumentException(
                    "VoteSession currently supports ranked single-winner polls only."
            );
        }

    }

    private List<PollOption> validateAndCopyOptions(List<PollOption> input) {
        Objects.requireNonNull(input, "options");

        if (input.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }

        List<PollOption> copy = new ArrayList<>(input);
        copy.sort(Comparator
                .comparingInt(PollOption::displayOrder)
                .thenComparingLong(PollOption::optionId));

        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (PollOption option : copy) {
            Objects.requireNonNull(option, "options must not contain null entries");

            if (option.optionId() < 1) {
                throw new IllegalArgumentException("option ids must be positive");
            }
            if (!seen.add(option.optionId())) {
                throw new IllegalArgumentException("options must not contain duplicate option ids");
            }
        }

        return List.copyOf(copy);
    }

    private Map<Long, PollOption> buildOptionsById(List<PollOption> orderedOptions) {
        Map<Long, PollOption> map = new LinkedHashMap<>();
        for (PollOption option : orderedOptions) {
            map.put(option.optionId(), option);
        }
        return Map.copyOf(map);
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public Poll poll() {
        return poll;
    }

    public long pollId() {
        return poll.pollId();
    }

    public VoteScreen currentScreen() {
        return currentScreen;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastInteractionAt() {
        return lastInteractionAt;
    }

    public List<PollOption> options() {
        return options;
    }

    public List<Long> rankedOptionIds() {
        return List.copyOf(rankedOptionIds);
    }

    public int selectedCount() {
        return rankedOptionIds.size();
    }

    public boolean hasSelections() {
        return !rankedOptionIds.isEmpty();
    }

    public boolean isInSelectionScreen() {
        return currentScreen == VoteScreen.SELECTION;
    }

    public boolean isInConfirmationScreen() {
        return currentScreen == VoteScreen.CONFIRMATION;
    }

    public int maxSelectableOptions() {
        int configuredMax = poll.maxRankings();
        int availableOptions = options.size();

        if (configuredMax > 0) {
            return Math.min(configuredMax, availableOptions);
        }

        return availableOptions;
    }

    public boolean isSelected(long optionId) {
        requireKnownOption(optionId);
        return rankedOptionIds.contains(optionId);
    }

    public Integer assignedRank(long optionId) {
        requireKnownOption(optionId);
        int index = rankedOptionIds.indexOf(optionId);
        return index >= 0 ? index + 1 : null;
    }

    public boolean canAssignAnotherRank() {
        return rankedOptionIds.size() < maxSelectableOptions();
    }

    /**
     * Toggle behaviour for ranked selection:
     * - clicking an unselected option assigns the next rank
     * - clicking a selected option removes it and collapses later ranks upward
     *
     * @return true if session state changed
     */
    public boolean toggleRankedSelection(long optionId) {
        requireSelectionScreen();

        if (isSelected(optionId)) {
            return removeRank(optionId);
        }

        return assignNextRank(optionId);
    }

    /**
     * Assigns the next available rank to the given option.
     *
     * @return true if a new rank was assigned, false if the session is already at capacity
     */
    public boolean assignNextRank(long optionId) {
        requireSelectionScreen();
        requireKnownOption(optionId);

        if (rankedOptionIds.contains(optionId)) {
            return false;
        }

        if (!canAssignAnotherRank()) {
            return false;
        }

        rankedOptionIds.add(optionId);
        touch();
        return true;
    }

    /**
     * Removes a selected option from the ranked list.
     * Remaining selections automatically shift upward to preserve contiguous ranks.
     *
     * @return true if the selection existed and was removed
     */
    public boolean removeRank(long optionId) {
        requireSelectionScreen();
        requireKnownOption(optionId);

        boolean removed = rankedOptionIds.remove(optionId);
        if (removed) {
            touch();
        }
        return removed;
    }

    public void clearSelections() {
        requireSelectionScreen();

        if (!rankedOptionIds.isEmpty()) {
            rankedOptionIds.clear();
            touch();
        }
    }

    /**
     * Lightweight session/UI validity only.
     *
     * This intentionally mirrors the player-facing interaction rules enough to
     * drive button state and summaries, but it is not authoritative validation.
     * The service layer remains the final source of truth.
     */
    public boolean isValidSelection() {
        if (rankedOptionIds.isEmpty()) {
            return false;
        }

        int selectedCount = rankedOptionIds.size();
        int maxSelectable = maxSelectableOptions();

        if (selectedCount > maxSelectable) {
            return false;
        }

        if (!poll.allowPartialRanking()) {
            return selectedCount == maxSelectable;
        }

        return true;
    }

    public void moveToConfirmation() {
        if (!isValidSelection()) {
            throw new IllegalStateException("Cannot move to confirmation with an invalid selection.");
        }

        this.currentScreen = VoteScreen.CONFIRMATION;
        touch();
    }

    public void returnToSelection() {
        this.currentScreen = VoteScreen.SELECTION;
        touch();
    }

    public Duration ageAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return Duration.between(createdAt, now);
    }

    public Duration idleTimeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return Duration.between(lastInteractionAt, now);
    }

    /**
     * Renderer-friendly ordered view of currently ranked options.
     */
    public List<RankedSelection> rankedSelections() {
        List<RankedSelection> out = new ArrayList<>();

        for (int i = 0; i < rankedOptionIds.size(); i++) {
            long optionId = rankedOptionIds.get(i);
            PollOption option = optionsById.get(optionId);

            out.add(new RankedSelection(
                    i + 1,
                    option.optionId(),
                    option.displayName(),
                    option.description()
            ));
        }

        return List.copyOf(out);
    }

    private void requireSelectionScreen() {
        if (currentScreen != VoteScreen.SELECTION) {
            throw new IllegalStateException("Selections can only be changed from the selection screen.");
        }
    }

    private void requireKnownOption(long optionId) {
        if (!optionsById.containsKey(optionId)) {
            throw new IllegalArgumentException(
                    "Option #" + optionId + " does not belong to poll #" + poll.pollId() + "."
            );
        }
    }

    private void touch() {
        this.lastInteractionAt = Instant.now();
    }

    public record RankedSelection(
            int rank,
            long optionId,
            String displayName,
            String description
    ) {
        public RankedSelection {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be >= 1");
            }
            if (optionId < 1) {
                throw new IllegalArgumentException("optionId must be >= 1");
            }
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(description, "description");
        }
    }
}