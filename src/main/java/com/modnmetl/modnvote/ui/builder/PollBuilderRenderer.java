package com.modnmetl.modnvote.ui.builder;

import com.modnmetl.modnvote.domain.PollOption;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PollBuilderRenderer {

    private static final int SIZE = 54;

    public void open(Player player, PollBuilderSession session) {
        Inventory inv = Bukkit.createInventory(
                new PollBuilderInventoryHolder(session),
                SIZE,
                "Poll Builder #" + session.getPollId()
        );

        inv.setItem(10, createItem(Material.NAME_TAG,
                "§bTitle",
                List.of("§7Click to edit title", "§f" + session.getPollSnapshot().title())));

        inv.setItem(12, createItem(Material.BOOK,
                "§bDescription",
                List.of("§7Click to edit description", "§f" + session.getPollSnapshot().description())));

        List<PollOption> options = session.getOptionsSnapshot();
        for (int i = 0; i < options.size(); i++) {
            PollOption option = options.get(i);
            int slot = 19 + i;
            inv.setItem(slot, createItem(Material.PAPER,
                    "§aOption " + (i + 1),
                    List.of("§7Left-click: edit name","§7Right-click: edit description","§f" + option.displayName())));
        }

        inv.setItem(49, createItem(Material.EMERALD,
                "§aValidate / Ready",
                List.of("§7Click to validate poll")));

        inv.setItem(53, createItem(Material.BARRIER,
                "§cCancel / Delete",
                List.of("§7Click to delete draft")));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
