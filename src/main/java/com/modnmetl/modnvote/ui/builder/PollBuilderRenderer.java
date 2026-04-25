package com.modnmetl.modnvote.ui.builder;

import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.service.PollService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PollBuilderRenderer {

    public static final int TITLE_SLOT = 10;
    public static final int DESCRIPTION_SLOT = 12;
    public static final int ALLOW_PARTIAL_SLOT = 14;
    public static final int MAX_RANKINGS_SLOT = 16;
    public static final int FIRST_OPTION_SLOT = 19;
    public static final int READY_SLOT = 49;
    public static final int CANCEL_SLOT = 53;

    private static final int WRAP_LENGTH = 40;
    private static final int MAX_VALIDATION_ISSUES_IN_LORE = 6;

    private final PollService pollService;

    public PollBuilderRenderer(PollService pollService) {
        this.pollService = pollService;
    }

    public void open(Player player, PollBuilderSession session) {
        Inventory inv = Bukkit.createInventory(
                new PollBuilderInventoryHolder(session),
                54,
                "Poll Builder"
        );

        Poll poll = session.getPollSnapshot();

        inv.setItem(TITLE_SLOT, createItem(Material.NAME_TAG,
                "§aTitle",
                buildFieldLore(poll.title(), "Untitled ranked poll", "Click to edit title")
        ));

        inv.setItem(DESCRIPTION_SLOT, createItem(Material.BOOK,
                "§aDescription",
                buildFieldLore(poll.description(), "No description set", "Click to edit description")
        ));

        if (poll.pollType() == PollType.RANKED_SINGLE_WINNER) {
            inv.setItem(ALLOW_PARTIAL_SLOT, createItem(Material.LEVER,
                    poll.allowPartialRanking() ? "§aAllow Partial Rankings: ON" : "§cAllow Partial Rankings: OFF",
                    buildAllowPartialLore(poll.allowPartialRanking())
            ));

            inv.setItem(MAX_RANKINGS_SLOT, createItem(Material.COMPARATOR,
                    "§aMax Rankings: §f" + formatMaxRankings(poll.maxRankings(), session.getOptionsSnapshot().size()),
                    buildMaxRankingsLore(poll.maxRankings(), session.getOptionsSnapshot().size())
            ));
        }

        List<PollOption> options = session.getOptionsSnapshot();
        for (int i = 0; i < options.size(); i++) {
            PollOption option = options.get(i);

            boolean nameComplete = isComplete(option.displayName()) && !isPlaceholderOptionName(option.displayName(), i + 1);
            String optionNameColour = nameComplete ? "§a" : "§c";
            String displayName = isComplete(option.displayName()) ? option.displayName() : "Option " + (i + 1);

            List<String> lore = new ArrayList<>();
            lore.add("§7Left-click: edit name");
            lore.add("§7Right-click: edit description");
            lore.add("§8");
            lore.add(nameComplete ? "§aName set" : "§cName still placeholder");

            if (isComplete(option.description()) && !isPlaceholderOptionDescription(option.description(), i + 1)) {
                lore.add("§aDescription set:");
                lore.addAll(wrapText("§a" + option.description(), WRAP_LENGTH));
            } else {
                lore.add("§cDescription missing or placeholder");
            }

            inv.setItem(FIRST_OPTION_SLOT + i, createItem(Material.PAPER,
                    optionNameColour + displayName,
                    lore
            ));
        }

        BuilderValidation validation = validateBuilder(session);

        if (validation.valid()) {
            inv.setItem(READY_SLOT, createItem(Material.LIME_WOOL,
                    "§aREADY",
                    List.of("§7Click to mark poll ready")
            ));
        } else {
            inv.setItem(READY_SLOT, createItem(Material.BARRIER,
                    "§cNOT READY",
                    buildValidationLore(validation.issues())
            ));
        }

        inv.setItem(CANCEL_SLOT, createItem(Material.RED_WOOL,
                "§cCancel",
                List.of("§7Close builder")
        ));

        player.openInventory(inv);
    }

    private List<String> buildAllowPartialLore(boolean enabled) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to toggle.");
        lore.add("§8");
        if (enabled) {
            lore.addAll(wrapText("§aPlayers may submit a ballot after ranking fewer than the maximum allowed choices.", WRAP_LENGTH));
        } else {
            lore.addAll(wrapText("§cPlayers must rank the required number of choices before submitting.", WRAP_LENGTH));
        }
        return lore;
    }

    private List<String> buildMaxRankingsLore(int maxRankings, int optionCount) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to cycle.");
        lore.add("§8");
        lore.addAll(wrapText("§7Controls how many choices each player may rank.", WRAP_LENGTH));
        lore.add("§8");
        lore.addAll(wrapText("§7Example: 6 options with Max Rankings 3 means players choose their top 3 only.", WRAP_LENGTH));
        lore.add("§8");
        lore.add("§7Current: §f" + formatMaxRankings(maxRankings, optionCount));
        return lore;
    }

    private String formatMaxRankings(int maxRankings, int optionCount) {
        if (maxRankings <= 0 || maxRankings >= optionCount) {
            return "ALL OPTIONS";
        }
        return String.valueOf(maxRankings);
    }

    private BuilderValidation validateBuilder(PollBuilderSession session) {
        List<String> issues = new ArrayList<>();
        Poll poll = session.getPollSnapshot();
        List<PollOption> options = session.getOptionsSnapshot();

        try {
            PollService.PollValidationResult serviceValidation = pollService.validatePollDefinition(session.getPollId());
            issues.addAll(serviceValidation.issues());
        } catch (Exception e) {
            issues.add("Validation failed: " + e.getMessage());
        }

        if (!isComplete(poll.title()) || poll.title().equalsIgnoreCase("Untitled ranked poll")) {
            issues.add("Poll title must be changed from the placeholder.");
        }
        if (!isComplete(poll.description())) {
            issues.add("Poll description must not be blank.");
        }

        for (int i = 0; i < options.size(); i++) {
            PollOption option = options.get(i);
            int displayNumber = i + 1;

            if (!isComplete(option.displayName()) || isPlaceholderOptionName(option.displayName(), displayNumber)) {
                issues.add("Option " + displayNumber + " name must be changed from the placeholder.");
            }
            if (!isComplete(option.description()) || isPlaceholderOptionDescription(option.description(), displayNumber)) {
                issues.add("Option " + displayNumber + " description must be changed from the placeholder.");
            }
        }

        return new BuilderValidation(issues.isEmpty(), List.copyOf(issues));
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

    private List<String> buildFieldLore(String value, String placeholder, String actionHint) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + actionHint);
        lore.add("§8");

        if (isComplete(value) && !value.equalsIgnoreCase(placeholder)) {
            lore.addAll(wrapText("§a" + value, WRAP_LENGTH));
        } else {
            lore.addAll(wrapText("§c" + placeholder, WRAP_LENGTH));
        }

        return lore;
    }

    private List<String> buildValidationLore(List<String> issues) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Complete the highlighted fields.");

        if (!issues.isEmpty()) {
            lore.add("§8");
            lore.add("§cIssues:");
            int displayed = 0;
            for (String issue : issues) {
                if (displayed >= MAX_VALIDATION_ISSUES_IN_LORE) {
                    lore.add("§7...and " + (issues.size() - displayed) + " more.");
                    break;
                }
                lore.addAll(wrapText("§c- " + issue, WRAP_LENGTH));
                displayed++;
            }
        }

        return lore;
    }

    private boolean isComplete(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isPlaceholderOptionName(String value, int displayNumber) {
        return value.equalsIgnoreCase("Option " + displayNumber);
    }

    private boolean isPlaceholderOptionDescription(String value, int displayNumber) {
        return value.equalsIgnoreCase("Placeholder description for option " + displayNumber + ".");
    }

    private List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String colorPrefix = extractLeadingColor(text, "§7");
        String cleanText = stripColourCodes(text);
        String[] words = cleanText.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLength) {
                if (!currentLine.isEmpty()) {
                    lines.add(colorPrefix + currentLine);
                }
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(colorPrefix + currentLine);
        }

        return lines;
    }

    private String extractLeadingColor(String text, String fallback) {
        if (text != null && text.length() >= 2 && text.charAt(0) == '§') {
            return text.substring(0, 2);
        }
        return fallback;
    }

    private String stripColourCodes(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    private record BuilderValidation(boolean valid, List<String> issues) {
    }
}
