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
 * In-memory manager for active yes/no vote sessions.
 */
public final class YesNoVoteSessionManager {

    private final Map<UUID, YesNoVoteSession> sessionsByPlayerId = new ConcurrentHashMap<>();
    private final Duration sessionTimeout;

    public YesNoVoteSessionManager(Duration sessionTimeout) {
        this.sessionTimeout = Objects.requireNonNull(sessionTimeout, "sessionTimeout");

        if (sessionTimeout.isZero() || sessionTimeout.isNegative()) {
            throw new IllegalArgumentException("sessionTimeout must be positive");
        }
    }

    public YesNoVoteSession createOrReplaceSession(UUID playerUuid,
                                                   Poll poll,
                                                   List<PollOption> options) {
        YesNoVoteSession session = new YesNoVoteSession(playerUuid, poll, options);
        sessionsByPlayerId.put(playerUuid, session);
        return session;
    }

    public Optional<YesNoVoteSession> findSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return Optional.ofNullable(sessionsByPlayerId.get(playerUuid));
    }

    public Optional<YesNoVoteSession> findSession(UUID playerUuid, long pollId) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        YesNoVoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null || session.pollId() != pollId) {
            return Optional.empty();
        }

        return Optional.of(session);
    }

    public YesNoVoteSession getRequiredSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        YesNoVoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null) {
            throw new IllegalStateException("No active yes/no vote session exists for player " + playerUuid + ".");
        }

        return session;
    }

    public boolean removeSession(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return sessionsByPlayerId.remove(playerUuid) != null;
    }

    public boolean removeSession(UUID playerUuid, long pollId) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        YesNoVoteSession session = sessionsByPlayerId.get(playerUuid);
        if (session == null || session.pollId() != pollId) {
            return false;
        }

        return sessionsByPlayerId.remove(playerUuid, session);
    }

    public int expireStaleSessions(Instant now) {
        Objects.requireNonNull(now, "now");

        int removed = 0;

        for (YesNoVoteSession session : List.copyOf(sessionsByPlayerId.values())) {
            if (session.idleTimeAt(now).compareTo(sessionTimeout) >= 0) {
                boolean didRemove = sessionsByPlayerId.remove(session.playerUuid(), session);
                if (didRemove) {
                    removed++;
                }
            }
        }

        return removed;
    }

    public Collection<YesNoVoteSession> activeSessions() {
        return List.copyOf(sessionsByPlayerId.values());
    }

    public void clearAllSessions() {
        sessionsByPlayerId.clear();
    }
}