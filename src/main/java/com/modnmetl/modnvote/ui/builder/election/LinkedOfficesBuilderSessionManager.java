package com.modnmetl.modnvote.ui.builder.election;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active {@link LinkedOfficesBuilderSession} instances per admin.
 *
 * Mirrors the existing builder/vote session manager pattern and stays isolated
 * from voting flows.
 */
public final class LinkedOfficesBuilderSessionManager {

    private final Map<UUID, LinkedOfficesBuilderSession> sessions = new ConcurrentHashMap<>();

    public void createOrReplaceSession(LinkedOfficesBuilderSession session) {
        sessions.put(session.getAdminId(), session);
    }

    public LinkedOfficesBuilderSession getSession(UUID adminId) {
        return sessions.get(adminId);
    }

    public void removeSession(UUID adminId) {
        sessions.remove(adminId);
    }

    public boolean hasSession(UUID adminId) {
        return sessions.containsKey(adminId);
    }
}
