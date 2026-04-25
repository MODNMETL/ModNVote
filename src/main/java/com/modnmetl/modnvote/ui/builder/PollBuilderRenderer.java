package com.modnmetl.modnvote.ui.builder;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PollBuilderRenderer {

    public void open(Player player, PollBuilderSession session) {
        Inventory inv = Bukkit.createInventory(
                new PollBuilderInventoryHolder(session),
                54,
                "Poll Builder"
        );

        Poll poll = session.getPollSnapshot();

        inv.setItem(10, createItem(Material.NAME_TAG,
                "§aTitle",
                buildDescription(poll.title(), "Click to edit title")
        ));

        inv.setItem(12, createItem(Material.BOOK,
                "§aDescription",
                buildDescription(poll.description(), "Click to edit description")
        ));

        List<PollOption> options = session.getOptionsSnapshot();
        for (int i = 0; i < options.size(); i++) {
            PollOption option = options.get(i);

            List<String> lore = new ArrayList<>();
            lore.add("§7Left-click: edit name");
            lore.add("§7Right-click: edit description");

            if (option.description() != null && !option.description().isBlank()) {
                lore.add("§8");
                lore.add("§8Description:");
                lore.addAll(wrapText("§7" + option.description(), 40));
            }

            inv.setItem(19 + i, createItem(Material.PAPER,
                    "§f" + option.displayName(),
                    lore
            ));
        }

        inv.setItem(49, createItem(Material.BARRIER,
                "§cNOT READY",
                List.of("§7Validation not implemented yet")
        ));

        inv.setItem(53, createItem(Material.RED_WOOL,
                "§cCancel",
                List.of("§7Close builder")
        ));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private List<String> buildDescription(String text, String fallback) {
        List<String> lore = new ArrayList<>();

        lore.add("§7" + fallback);

        if (text != null && !text.isBlank()) {
            lore.add("§8");
            lore.addAll(wrapText("§7" + text, 40));
        }

        return lore;
    }

    private List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();

        String colorPrefix = "§7";
        String cleanText = text.replace("§7", "");

        String[] words = cleanText.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLength) {
                lines.add(colorPrefix + currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(colorPrefix + currentLine.toString());
        }

        return lines;
    }
}
