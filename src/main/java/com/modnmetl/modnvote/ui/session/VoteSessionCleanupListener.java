package com.modnmetl.modnvote.ui.session;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Cleans up vote sessions when players leave the server.
 */
public final class VoteSessionCleanupListener implements Listener {

    private final VoteSessionManager voteSessionManager;

    public VoteSessionCleanupListener(VoteSessionManager voteSessionManager) {
        this.voteSessionManager = Objects.requireNonNull(voteSessionManager, "voteSessionManager");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        voteSessionManager.removeSession(event.getPlayer().getUniqueId());
    }
}