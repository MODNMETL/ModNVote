package com.modnmetl.modnvote.ui.session.election;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.election.ElectionDefinition;

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
 * In-memory manager for active linked-offices vote sessions.
 *
 * <p>Mirrors {@code YesNoVoteSessionManager}: one active session per player,
 * keyed by player UUID, with idle expiry. Bukkit-free so it is unit-testable.
 */
public final class LinkedOfficesVoteSessionManager {

    private final Map<UUID, LinkedOfficesVoteSession> sessionsByPlayerId = new ConcurrentHashMap<>();
    private final Duration sessionTimeout;

    public LinkedOfficesVoteSessionManager(Duration sessionTimeout) {
        this.sessionTimeout = Objects.requireNonNull(sessionTimeout, "sessionTimeout");
        if (sessionTimeout.isZero() || sessionTimeout.isNegative()) {
            throw new IllegalArgumentException("sessionTimeout must be positive");
        }
    }

    public LinkedOfficesVoteSession createOrReplaceSession(UUID playerUuid,
                                                           Poll poll,
                                                           ElectionDefinition definition) {
        LinkedOfficesVoteSession session = new LinkedOfficesVoteSession(playerUuid, poll, definition);
        sessionsByPlayerId.put(playerUuid, session);
        return session;
    }

    public Optional<LinkedOfficesVoteSession> findSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return Optional.ofNullable(sessionsByPlayerId.get(playerUuid));
    }

    public Optional<LinkedOfficesVoteSession> findSession(UUID playerUuid, long pollId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        LinkedOfficesVoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null || session.pollId() != pollId) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public LinkedOfficesVoteSession getRequiredSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        LinkedOfficesVoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null) {
            throw new IllegalStateException("No active linked-offices vote session exists for player " + playerUuid + ".");
        }
        return session;
    }

    public boolean removeSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return sessionsByPlayerId.remove(playerUuid) != null;
    }

    public boolean removeSession(UUID playerUuid, long pollId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        LinkedOfficesVoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null || session.pollId() != pollId) {
            return false;
        }
        return sessionsByPlayerId.remove(playerUuid, session);
    }

    public int expireStaleSessions(Instant now) {
        Objects.requireNonNull(now, "now");
        int removed = 0;
        for (LinkedOfficesVoteSession session : List.copyOf(sessionsByPlayerId.values())) {
            if (session.idleTimeAt(now).compareTo(sessionTimeout) >= 0) {
                if (sessionsByPlayerId.remove(session.playerUuid(), session)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public Collection<LinkedOfficesVoteSession> activeSessions() {
        return List.copyOf(sessionsByPlayerId.values());
    }

    public void clearAllSessions() {
        sessionsByPlayerId.clear();
    }
}
