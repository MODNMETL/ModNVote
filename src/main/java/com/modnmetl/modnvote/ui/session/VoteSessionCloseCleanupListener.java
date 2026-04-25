package com.modnmetl.modnvote.ui.session;

import com.modnmetl.modnvote.ui.render.JavaInventoryVoteRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * Cleans up vote sessions when a player genuinely closes a managed vote GUI.
 *
 * This listener deliberately distinguishes between:
 * - intentional renderer reopen/refresh transitions
 * - actual player-driven closure
 */
public final class VoteSessionCloseCleanupListener implements Listener {

    private final JavaPlugin plugin;
    private final VoteSessionManager voteSessionManager;
    private final JavaInventoryVoteRenderer voteRenderer;

    public VoteSessionCloseCleanupListener(JavaPlugin plugin,
                                           VoteSessionManager voteSessionManager,
                                           JavaInventoryVoteRenderer voteRenderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.voteSessionManager = Objects.requireNonNull(voteSessionManager, "voteSessionManager");
        this.voteRenderer = Objects.requireNonNull(voteRenderer, "voteRenderer");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!voteRenderer.isManagedInventory(event.getInventory())) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (voteRenderer.isManagedReopenInProgress(playerUuid)) {
                return;
            }

            if (player.getOpenInventory() != null
                    && voteRenderer.isManagedInventory(player.getOpenInventory().getTopInventory())) {
                return;
            }

            voteSessionManager.removeSession(playerUuid);
        }, 1L);
    }
}