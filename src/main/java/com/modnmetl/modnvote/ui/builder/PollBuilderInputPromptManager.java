package com.modnmetl.modnvote.ui.builder;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Bridges chat input to builder actions.
 *
 * This does NOT perform persistence. It simply captures input
 * and forwards it to a provided handler.
 */
public class PollBuilderInputPromptManager {

    private final Map<UUID, Consumer<String>> pendingInputs = new ConcurrentHashMap<>();

    public void prompt(Player player, Consumer<String> handler) {
        pendingInputs.put(player.getUniqueId(), handler);
        player.sendMessage("§eEnter value in chat or type 'cancel'.");
    }

    public boolean handleChat(Player player, String message) {
        Consumer<String> handler = pendingInputs.remove(player.getUniqueId());
        if (handler == null) {
            return false;
        }

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Input cancelled.");
            return true;
        }

        handler.accept(message);
        return true;
    }

    public boolean hasPendingInput(UUID playerId) {
        return pendingInputs.containsKey(playerId);
    }
}
