package com.modnmetl.modnvote.ui.builder.election;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Dedicated inventory holder for the linked-offices builder GUI, keeping it
 * cleanly separated from voting and poll-builder flows.
 */
public final class LinkedOfficesBuilderHolder implements InventoryHolder {

    private final LinkedOfficesBuilderSession session;

    public LinkedOfficesBuilderHolder(LinkedOfficesBuilderSession session) {
        this.session = session;
    }

    public LinkedOfficesBuilderSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null; // not used
    }
}
