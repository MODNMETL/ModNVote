package com.modnmetl.modnvote.ui.builder.election;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Routes chat input to the linked-offices builder input manager. Independent of
 * the poll-builder chat listener so the two flows never cross-talk.
 */
public final class LinkedOfficesBuilderChatListener implements Listener {

    private final LinkedOfficesInputPromptManager inputManager;

    public LinkedOfficesBuilderChatListener(LinkedOfficesInputPromptManager inputManager) {
        this.inputManager = inputManager;
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (inputManager.handleChat(player, event.getMessage())) {
            event.setCancelled(true);
        }
    }
}
