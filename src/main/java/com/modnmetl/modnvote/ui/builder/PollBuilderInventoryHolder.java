package com.modnmetl.modnvote.ui.builder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Dedicated holder for Poll Builder GUI.
 * Ensures clean separation from voting flows.
 */
public class PollBuilderInventoryHolder implements InventoryHolder {

    private final PollBuilderSession session;

    public PollBuilderInventoryHolder(PollBuilderSession session) {
        this.session = session;
    }

    public PollBuilderSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null; // not used
    }
}
