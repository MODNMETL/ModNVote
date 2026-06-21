package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.ui.session.election.LinkedOfficesVoteScreen;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/**
 * Custom inventory holder for linked-offices vote GUIs.
 *
 * <p>Kept separate from {@link ModNVoteInventoryHolder} (which models the
 * single-contest SELECTION/CONFIRMATION flow) so the multi-screen linked-offices
 * flow can carry its own {@link LinkedOfficesVoteScreen} and the office key being
 * edited without disturbing the existing yes/no and ranked holders.
 */
public final class LinkedOfficesInventoryHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final long pollId;
    private final LinkedOfficesVoteScreen screen;
    private final String officeKey;

    public LinkedOfficesInventoryHolder(UUID playerUuid,
                                        long pollId,
                                        LinkedOfficesVoteScreen screen,
                                        String officeKey) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.screen = Objects.requireNonNull(screen, "screen");
        if (pollId < 1) {
            throw new IllegalArgumentException("pollId must be positive");
        }
        this.pollId = pollId;
        this.officeKey = officeKey;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public long pollId() {
        return pollId;
    }

    public LinkedOfficesVoteScreen screen() {
        return screen;
    }

    /** The office being edited on an OFFICE screen, or {@code null} on other screens. */
    public String officeKey() {
        return officeKey;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
