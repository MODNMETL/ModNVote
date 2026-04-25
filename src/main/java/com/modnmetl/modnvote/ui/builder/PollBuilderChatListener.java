package com.modnmetl.modnvote.ui.builder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PollBuilderChatListener implements Listener {

    private final PollBuilderInputPromptManager inputManager;

    public PollBuilderChatListener(PollBuilderInputPromptManager inputManager) {
        this.inputManager = inputManager;
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (inputManager.handleChat(player, message)) {
            event.setCancelled(true);
        }
    }
}
