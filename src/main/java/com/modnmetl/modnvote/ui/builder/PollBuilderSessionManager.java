package com.modnmetl.modnvote.ui.builder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active PollBuilderSession instances per player.
 *
 * Mirrors VoteSessionManager pattern but remains isolated from voting flow.
 */
public class PollBuilderSessionManager {

    private final Map<UUID, PollBuilderSession> sessions = new ConcurrentHashMap<>();

    public void createOrReplaceSession(PollBuilderSession session) {
        sessions.put(session.getPlayerId(), session);
    }

    public PollBuilderSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public PollBuilderSession getRequiredSession(UUID playerId) {
        PollBuilderSession session = sessions.get(playerId);
        if (session == null) {
            throw new IllegalStateException("No PollBuilderSession exists for player " + playerId);
        }
        return session;
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }
}
