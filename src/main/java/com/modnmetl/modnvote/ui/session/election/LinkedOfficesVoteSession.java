package com.modnmetl.modnvote.ui.session.election;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory session model for one player's active linked-offices voting
 * interaction.
 *
 * <p>This holds no Bukkit types: it pairs the voter with their poll and the
 * Bukkit-free {@link LinkedOfficesVoteState} that accumulates their selections,
 * plus the current {@link LinkedOfficesVoteScreen} and (when on an office screen)
 * the office being edited. The renderer/listener layer drives screen transitions;
 * all ballot state lives in {@link #state()}.
 */
public final class LinkedOfficesVoteSession {

    private final UUID playerUuid;
    private final Poll poll;
    private final LinkedOfficesVoteState state;
    private final Instant createdAt;

    private LinkedOfficesVoteScreen currentScreen;
    private String currentOfficeKey;
    private Instant lastInteractionAt;

    public LinkedOfficesVoteSession(UUID playerUuid, Poll poll, ElectionDefinition definition) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.poll = Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(definition, "definition");

        if (poll.pollType() != PollType.LINKED_OFFICES) {
            throw new IllegalArgumentException("LinkedOfficesVoteSession supports LINKED_OFFICES polls only.");
        }

        this.state = new LinkedOfficesVoteState(definition);
        this.createdAt = Instant.now();
        this.lastInteractionAt = this.createdAt;
        this.currentScreen = LinkedOfficesVoteScreen.OVERVIEW;
        this.currentOfficeKey = null;
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

    public LinkedOfficesVoteState state() {
        return state;
    }

    public LinkedOfficesVoteScreen currentScreen() {
        return currentScreen;
    }

    public String currentOfficeKey() {
        return currentOfficeKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastInteractionAt() {
        return lastInteractionAt;
    }

    public boolean isInOverviewScreen() {
        return currentScreen == LinkedOfficesVoteScreen.OVERVIEW;
    }

    public boolean isInOfficeScreen() {
        return currentScreen == LinkedOfficesVoteScreen.OFFICE;
    }

    public boolean isInReviewScreen() {
        return currentScreen == LinkedOfficesVoteScreen.REVIEW;
    }

    public void openOffice(String officeKey) {
        state.requireContest(officeKey);
        this.currentOfficeKey = officeKey;
        this.currentScreen = LinkedOfficesVoteScreen.OFFICE;
        touch();
    }

    public void returnToOverview() {
        this.currentOfficeKey = null;
        this.currentScreen = LinkedOfficesVoteScreen.OVERVIEW;
        touch();
    }

    public void moveToReview() {
        this.currentOfficeKey = null;
        this.currentScreen = LinkedOfficesVoteScreen.REVIEW;
        touch();
    }

    public void touch() {
        this.lastInteractionAt = Instant.now();
    }

    public Duration idleTimeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return Duration.between(lastInteractionAt, now);
    }
}
