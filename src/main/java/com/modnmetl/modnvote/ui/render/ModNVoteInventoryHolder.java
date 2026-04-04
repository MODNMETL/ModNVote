package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.ui.session.VoteScreen;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/**
 * Custom inventory holder for ModNVote GUI inventories.
 *
 * This lets listeners identify managed vote inventories safely without relying
 * only on inventory titles.
 */
public final class ModNVoteInventoryHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final long pollId;
    private final VoteScreen screen;

    public ModNVoteInventoryHolder(UUID playerUuid, long pollId, VoteScreen screen) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.screen = Objects.requireNonNull(screen, "screen");

        if (pollId < 1) {
            throw new IllegalArgumentException("pollId must be positive");
        }

        this.pollId = pollId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public long pollId() {
        return pollId;
    }

    public VoteScreen screen() {
        return screen;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}