package com.modnmetl.modnvote.ui.session.election;

import com.modnmetl.modnvote.ui.render.LinkedOfficesVoteRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * Cleans up linked-offices vote sessions when a player genuinely closes a managed
 * vote GUI (rather than the renderer reopening one mid-flow).
 */
public final class LinkedOfficesVoteSessionCloseCleanupListener implements Listener {

    private final JavaPlugin plugin;
    private final LinkedOfficesVoteSessionManager sessionManager;
    private final LinkedOfficesVoteRenderer renderer;

    public LinkedOfficesVoteSessionCloseCleanupListener(JavaPlugin plugin,
                                                        LinkedOfficesVoteSessionManager sessionManager,
                                                        LinkedOfficesVoteRenderer renderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!renderer.isManagedInventory(event.getInventory())) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (renderer.isManagedReopenInProgress(playerUuid)) {
                return;
            }
            if (player.getOpenInventory() != null
                    && renderer.isManagedInventory(player.getOpenInventory().getTopInventory())) {
                return;
            }
            sessionManager.removeSession(playerUuid);
        }, 1L);
    }
}
