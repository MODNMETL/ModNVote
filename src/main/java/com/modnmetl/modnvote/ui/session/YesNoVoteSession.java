package com.modnmetl.modnvote.ui.session;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory session model for one player's active yes/no voting interaction.
 *
 * This class is part of the UI/session layer only.
 */
public final class YesNoVoteSession {

    private final UUID playerUuid;
    private final Poll poll;
    private final List<PollOption> options;
    private final Instant createdAt;

    private VoteScreen currentScreen;
    private Instant lastInteractionAt;
    private Long selectedOptionId;

    public YesNoVoteSession(UUID playerUuid,
                            Poll poll,
                            List<PollOption> options) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.poll = Objects.requireNonNull(poll, "poll");
        this.options = validateAndCopyOptions(options);
        this.createdAt = Instant.now();
        this.lastInteractionAt = this.createdAt;
        this.currentScreen = VoteScreen.SELECTION;
        this.selectedOptionId = null;

        if (poll.pollType() != PollType.YES_NO) {
            throw new IllegalArgumentException("YesNoVoteSession currently supports YES_NO polls only.");
        }
    }

    private List<PollOption> validateAndCopyOptions(List<PollOption> input) {
        Objects.requireNonNull(input, "options");
        if (input.size() != 2) {
            throw new IllegalArgumentException("YES_NO sessions require exactly 2 options.");
        }

        List<PollOption> copy = new ArrayList<>(input);
        copy.sort(Comparator
                .comparingInt(PollOption::displayOrder)
                .thenComparingLong(PollOption::optionId));

        for (PollOption option : copy) {
            Objects.requireNonNull(option, "options must not contain null entries");
            if (option.optionId() < 1) {
                throw new IllegalArgumentException("option ids must be positive");
            }
        }

        return List.copyOf(copy);
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

    public List<PollOption> options() {
        return options;
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

    public Long selectedOptionId() {
        return selectedOptionId;
    }

    public boolean hasSelection() {
        return selectedOptionId != null;
    }

    public boolean isSelected(long optionId) {
        requireKnownOption(optionId);
        return Objects.equals(selectedOptionId, optionId);
    }

    public PollOption selectedOption() {
        if (selectedOptionId == null) {
            return null;
        }

        for (PollOption option : options) {
            if (option.optionId() == selectedOptionId) {
                return option;
            }
        }
        return null;
    }

    public String selectedDisplayName() {
        PollOption selected = selectedOption();
        return selected != null ? selected.displayName() : null;
    }

    public boolean isInSelectionScreen() {
        return currentScreen == VoteScreen.SELECTION;
    }

    public boolean isInConfirmationScreen() {
        return currentScreen == VoteScreen.CONFIRMATION;
    }

    public boolean isValidSelection() {
        return selectedOptionId != null;
    }

    public boolean selectOption(long optionId) {
        requireSelectionScreen();
        requireKnownOption(optionId);

        if (Objects.equals(selectedOptionId, optionId)) {
            return false;
        }

        selectedOptionId = optionId;
        touch();
        return true;
    }

    public boolean clearSelection() {
        requireSelectionScreen();

        if (selectedOptionId == null) {
            return false;
        }

        selectedOptionId = null;
        touch();
        return true;
    }

    public boolean toggleSelection(long optionId) {
        requireSelectionScreen();
        requireKnownOption(optionId);

        if (Objects.equals(selectedOptionId, optionId)) {
            return clearSelection();
        }

        return selectOption(optionId);
    }

    public void moveToConfirmation() {
        if (!isValidSelection()) {
            throw new IllegalStateException("Cannot move to confirmation without a selected yes/no choice.");
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

    private void requireSelectionScreen() {
        if (currentScreen != VoteScreen.SELECTION) {
            throw new IllegalStateException("Selection can only be changed from the selection screen.");
        }
    }

    private void requireKnownOption(long optionId) {
        for (PollOption option : options) {
            if (option.optionId() == optionId) {
                return;
            }
        }
        throw new IllegalArgumentException("Option #" + optionId + " does not belong to poll #" + poll.pollId() + ".");
    }

    private void touch() {
        this.lastInteractionAt = Instant.now();
    }
}