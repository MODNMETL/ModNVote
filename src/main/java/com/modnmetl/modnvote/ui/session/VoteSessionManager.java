package com.modnmetl.modnvote.ui.session;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory manager for active vote sessions.
 *
 * Responsibilities:
 * - create and store sessions per player
 * - replace any existing session for a player when a new one is opened
 * - retrieve active sessions
 * - remove sessions explicitly on completion/cancel
 * - expire stale sessions
 *
 * This manager is intentionally persistence-free.
 * It owns only temporary UI/session state.
 */
public final class VoteSessionManager {

    private final Map<UUID, VoteSession> sessionsByPlayerId = new ConcurrentHashMap<>();
    private final Duration sessionTimeout;

    public VoteSessionManager(Duration sessionTimeout) {
        this.sessionTimeout = Objects.requireNonNull(sessionTimeout, "sessionTimeout");

        if (sessionTimeout.isZero() || sessionTimeout.isNegative()) {
            throw new IllegalArgumentException("sessionTimeout must be positive");
        }
    }

    public Duration sessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Creates and stores a new session for the player, replacing any previous one.
     */
    public VoteSession createOrReplaceSession(UUID playerUuid,
                                              Poll poll,
                                              List<PollOption> options) {
        VoteSession session = new VoteSession(playerUuid, poll, options);
        sessionsByPlayerId.put(playerUuid, session);
        return session;
    }

    public Optional<VoteSession> findSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return Optional.ofNullable(sessionsByPlayerId.get(playerUuid));
    }

    public VoteSession getRequiredSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        VoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null) {
            throw new IllegalStateException("No active vote session exists for player " + playerUuid + ".");
        }

        return session;
    }

    public boolean hasSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return sessionsByPlayerId.containsKey(playerUuid);
    }

    public boolean removeSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return sessionsByPlayerId.remove(playerUuid) != null;
    }

    public Optional<VoteSession> findSession(UUID playerUuid, long pollId) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        VoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null || session.pollId() != pollId) {
            return Optional.empty();
        }

        return Optional.of(session);
    }

    public boolean removeSession(UUID playerUuid, long pollId) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        VoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null || session.pollId() != pollId) {
            return false;
        }

        return sessionsByPlayerId.remove(playerUuid, session);
    }

    public int activeSessionCount() {
        return sessionsByPlayerId.size();
    }

    public Collection<VoteSession> activeSessions() {
        return List.copyOf(sessionsByPlayerId.values());
    }

    /**
     * Expires sessions whose idle time is greater than or equal to the configured timeout.
     *
     * @return the number of expired sessions removed
     */
    public int expireStaleSessions(Instant now) {
        Objects.requireNonNull(now, "now");

        int removed = 0;

        for (VoteSession session : List.copyOf(sessionsByPlayerId.values())) {
            if (session.idleTimeAt(now).compareTo(sessionTimeout) >= 0) {
                boolean didRemove = sessionsByPlayerId.remove(session.playerUuid(), session);
                if (didRemove) {
                    removed++;
                }
            }
        }

        return removed;
    }

    /**
     * Removes all active sessions.
     *
     * Intended for controlled shutdown/testing flows.
     */
    public void clearAllSessions() {
        sessionsByPlayerId.clear();
    }
}