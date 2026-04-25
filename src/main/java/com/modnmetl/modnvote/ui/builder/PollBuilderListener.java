package com.modnmetl.modnvote.ui.builder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

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
        int slot = event.getSlot();

        if (slot == 10) {
            player.closeInventory();
            inputManager.prompt(player, input -> player.sendMessage("§7Title set: " + input));
            return;
        }

        if (slot == 12) {
            player.closeInventory();
            inputManager.prompt(player, input -> player.sendMessage("§7Description updated"));
            return;
        }

        if (slot >= 19 && slot < 19 + session.getOptionsSnapshot().size()) {
            player.closeInventory();
            if (event.isRightClick()) {
                inputManager.prompt(player, input -> player.sendMessage("§7Option description updated"));
            } else {
                inputManager.prompt(player, input -> player.sendMessage("§7Option name updated"));
            }
            return;
        }

        if (slot == 49) {
            player.sendMessage("§7Validate clicked");
            return;
        }

        if (slot == 53) {
            player.sendMessage("§cCancel clicked");
        }
    }
}
