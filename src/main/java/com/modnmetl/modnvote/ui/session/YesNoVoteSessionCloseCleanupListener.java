package com.modnmetl.modnvote.ui.session;

import com.modnmetl.modnvote.ui.render.YesNoInventoryVoteRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * Cleans up yes/no vote sessions when a player genuinely closes a managed vote GUI.
 */
public final class YesNoVoteSessionCloseCleanupListener implements Listener {

    private final JavaPlugin plugin;
    private final YesNoVoteSessionManager yesNoVoteSessionManager;
    private final YesNoInventoryVoteRenderer yesNoRenderer;

    public YesNoVoteSessionCloseCleanupListener(JavaPlugin plugin,
                                                YesNoVoteSessionManager yesNoVoteSessionManager,
                                                YesNoInventoryVoteRenderer yesNoRenderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.yesNoVoteSessionManager = Objects.requireNonNull(yesNoVoteSessionManager, "yesNoVoteSessionManager");
        this.yesNoRenderer = Objects.requireNonNull(yesNoRenderer, "yesNoRenderer");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!yesNoRenderer.isManagedInventory(event.getInventory())) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (yesNoRenderer.isManagedReopenInProgress(playerUuid)) {
                return;
            }

            if (player.getOpenInventory() != null
                    && yesNoRenderer.isManagedInventory(player.getOpenInventory().getTopInventory())) {
                return;
            }

            yesNoVoteSessionManager.removeSession(playerUuid);
        }, 1L);
    }
}