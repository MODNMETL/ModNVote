package com.modnmetl.modnvote.ui.builder.election;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Bridges chat input to linked-offices builder actions.
 *
 * Captures a single pending text input per admin and forwards it to the provided
 * handler. It performs no persistence and is independent of the poll-builder
 * input manager so the two flows never cross-talk.
 */
public final class LinkedOfficesInputPromptManager {

    private final Map<UUID, Consumer<String>> pendingInputs = new ConcurrentHashMap<>();

    public void prompt(Player player, String fieldLabel, Consumer<String> handler) {
        pendingInputs.put(player.getUniqueId(), handler);
        player.sendMessage("§eEnter " + fieldLabel + " in chat, or type 'cancel'.");
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

    public void clear(UUID playerId) {
        pendingInputs.remove(playerId);
    }
}
