package com.modnmetl.modnvote.ui.session;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Cleans up yes/no vote sessions when players leave the server.
 */
public final class YesNoVoteSessionCleanupListener implements Listener {

    private final YesNoVoteSessionManager yesNoVoteSessionManager;

    public YesNoVoteSessionCleanupListener(YesNoVoteSessionManager yesNoVoteSessionManager) {
        this.yesNoVoteSessionManager = Objects.requireNonNull(yesNoVoteSessionManager, "yesNoVoteSessionManager");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        yesNoVoteSessionManager.removeSession(event.getPlayer().getUniqueId());
    }
}