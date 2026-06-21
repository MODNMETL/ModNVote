package com.modnmetl.modnvote.ui.session.election;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Cleans up linked-offices vote sessions when players leave the server.
 */
public final class LinkedOfficesVoteSessionCleanupListener implements Listener {

    private final LinkedOfficesVoteSessionManager sessionManager;

    public LinkedOfficesVoteSessionCleanupListener(LinkedOfficesVoteSessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }
}
