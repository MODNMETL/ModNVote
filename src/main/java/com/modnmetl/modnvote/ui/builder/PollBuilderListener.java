package com.modnmetl.modnvote.ui.builder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles interaction within the Poll Builder GUI.
 *
 * Scaffold only – routing logic will be expanded next.
 */
public class PollBuilderListener implements Listener {

    private final PollBuilderSessionManager sessionManager;
    private final PollBuilderInputPromptManager inputManager;

    public PollBuilderListener(PollBuilderSessionManager sessionManager,
                               PollBuilderInputPromptManager inputManager) {
        this.sessionManager = sessionManager;
        this.inputManager = inputManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof PollBuilderInventoryHolder builderHolder)) return;

        event.setCancelled(true);

        PollBuilderSession session = builderHolder.getSession();

        player.sendMessage("§7[PollBuilder] Click captured (slot=" + event.getSlot() + ") for poll #" + session.getPollId());

        // TODO: route clicks to edit title / description / options
        // TODO: integrate inputManager.prompt(...)
    }
}
