package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.ui.format.BallotSummaryFormatter;
import com.modnmetl.modnvote.ui.session.VoteScreen;
import com.modnmetl.modnvote.ui.session.VoteSession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Initial Java inventory renderer for ranked voting sessions.
 *
 * This renderer now establishes a fixed first-pass layout for:
 * - selection screen
 * - confirmation screen
 * - option placement
 * - summary item
 * - reset/cast controls
 *
 * Click handling is intentionally not part of this class and will be added
 * through a dedicated listener in the next phase, using this renderer's slot
 * mapping as the single source of truth.
 */
public final class JavaInventoryVoteRenderer implements VoteRenderer {

    private static final int SELECTION_SIZE = 54;
    private static final int CONFIRMATION_SIZE = 27;

    private static final String SELECTION_TITLE_PREFIX = "Vote: ";
    private static final String CONFIRMATION_TITLE_PREFIX = "Confirm Vote: ";

    private static final int INFO_SLOT = 4;
    private static final int SUMMARY_SLOT = 49;
    private static final int RESET_SLOT = 45;
    private static final int CAST_SLOT = 53;

    private static final int CONFIRM_SUMMARY_SLOT = 13;
    private static final int CONFIRM_BACK_SLOT = 11;
    private static final int CONFIRM_COMMIT_SLOT = 15;

    /**
     * First-pass option grid for ranked voting.
     * This gives us up to 21 option positions in a stable visual layout.
     */
    private static final int[] OPTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final BallotSummaryFormatter ballotSummaryFormatter;

    public JavaInventoryVoteRenderer(BallotSummaryFormatter ballotSummaryFormatter) {
        this.ballotSummaryFormatter = Objects.requireNonNull(ballotSummaryFormatter, "ballotSummaryFormatter");
    }

    @Override
    public void openSelection(Player player, VoteSession session) {
        VoteRenderer.requirePlayerAndSession(player, session);

        Inventory inventory = Bukkit.createInventory(
                new ModNVoteInventoryHolder(player.getUniqueId(), session.pollId(), VoteScreen.SELECTION),
                SELECTION_SIZE,
                buildSelectionTitle(session)
        );

        populateSelectionInventory(inventory, session);
        player.openInventory(inventory);
    }

    @Override
    public void openConfirmation(Player player, VoteSession session) {
        VoteRenderer.requirePlayerAndSession(player, session);

        Inventory inventory = Bukkit.createInventory(
                new ModNVoteInventoryHolder(player.getUniqueId(), session.pollId(), VoteScreen.CONFIRMATION),
                CONFIRMATION_SIZE,
                buildConfirmationTitle(session)
        );

        populateConfirmationInventory(inventory, session);
        player.openInventory(inventory);
    }

    @Override
    public void refresh(Player player, VoteSession session) {
        VoteRenderer.requirePlayerAndSession(player, session);

        if (session.isInConfirmationScreen()) {
            openConfirmation(player, session);
            return;
        }

        openSelection(player, session);
    }

    public boolean isSelectionOptionSlot(int rawSlot) {
        for (int optionSlot : OPTION_SLOTS) {
            if (optionSlot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    public int optionIndexForSlot(int rawSlot) {
        for (int i = 0; i < OPTION_SLOTS.length; i++) {
            if (OPTION_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    public boolean isResetSlot(int rawSlot) {
        return rawSlot == RESET_SLOT;
    }

    public boolean isCastSlot(int rawSlot) {
        return rawSlot == CAST_SLOT;
    }

    public boolean isConfirmationBackSlot(int rawSlot) {
        return rawSlot == CONFIRM_BACK_SLOT;
    }

    public boolean isConfirmationCommitSlot(int rawSlot) {
        return rawSlot == CONFIRM_COMMIT_SLOT;
    }

    public boolean isManagedInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        return inventory.getHolder() instanceof ModNVoteInventoryHolder;
    }

    public ModNVoteInventoryHolder requireManagedHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof ModNVoteInventoryHolder holder)) {
            throw new IllegalArgumentException("Inventory is not managed by ModNVote.");
        }
        return holder;
    }

    public boolean holderMatchesSessionScreen(ModNVoteInventoryHolder holder, VoteSession session) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(session, "session");

        return holder.screen() == session.currentScreen();
    }

    public boolean isManagedSelectionTitle(String title) {
        return title != null && title.startsWith(SELECTION_TITLE_PREFIX);
    }

    public boolean isManagedConfirmationTitle(String title) {
        return title != null && title.startsWith(CONFIRMATION_TITLE_PREFIX);
    }

    private String buildSelectionTitle(VoteSession session) {
        return truncateTitle(SELECTION_TITLE_PREFIX + session.poll().title());
    }

    private String buildConfirmationTitle(VoteSession session) {
        return truncateTitle(CONFIRMATION_TITLE_PREFIX + session.poll().title());
    }

    private String truncateTitle(String rawTitle) {
        Objects.requireNonNull(rawTitle, "rawTitle");

        final int maxLength = 32; // Bukkit inventory title practical limit
        if (rawTitle.length() <= maxLength) {
            return rawTitle;
        }

        return rawTitle.substring(0, maxLength - 3) + "...";
    }

    private void populateSelectionInventory(Inventory inventory, VoteSession session) {
        fillInventory(inventory, createFillerPane());

        inventory.setItem(INFO_SLOT, buildPollInfoItem(session));
        inventory.setItem(SUMMARY_SLOT, buildSelectionSummaryItem(session));
        inventory.setItem(RESET_SLOT, buildResetItem());
        inventory.setItem(CAST_SLOT, buildCastItem(session));

        List<PollOption> options = session.options();
        int renderCount = Math.min(options.size(), OPTION_SLOTS.length);

        for (int i = 0; i < renderCount; i++) {
            PollOption option = options.get(i);
            int slot = OPTION_SLOTS[i];
            inventory.setItem(slot, buildOptionItem(session, option));
        }
    }

    private void populateConfirmationInventory(Inventory inventory, VoteSession session) {
        fillInventory(inventory, createFillerPane());

        inventory.setItem(CONFIRM_SUMMARY_SLOT, buildConfirmationSummaryItem(session));
        inventory.setItem(CONFIRM_BACK_SLOT, buildBackItem());
        inventory.setItem(CONFIRM_COMMIT_SLOT, buildCommitItem());
    }

    private void fillInventory(Inventory inventory, ItemStack itemStack) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, itemStack);
        }
    }

    private ItemStack createFillerPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildPollInfoItem(VoteSession session) {
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        lore.add("Poll: " + session.poll().title());
        lore.add("Type: " + readablePollType(session));
        lore.add("Max rankings: " + session.maxSelectableOptions());
        lore.add("Partial ranking: " + (session.poll().allowPartialRanking() ? "Allowed" : "Not allowed"));

        return createItem(
                Material.BOOK,
                "Poll Information",
                lore
        );
    }

    private ItemStack buildOptionItem(VoteSession session, PollOption option) {
        Integer rank = session.assignedRank(option.optionId());

        List<String> lore = new ArrayList<>();
        lore.add(option.description());
        lore.add(" ");

        if (rank != null) {
            lore.add("Currently ranked: #" + rank);
            lore.add("Click to remove this option.");
        } else if (session.canAssignAnotherRank()) {
            lore.add("Not currently ranked.");
            lore.add("Click to assign the next rank.");
        } else {
            lore.add("Not currently ranked.");
            lore.add("You have reached the ranking limit.");
        }

        Material material = rank != null ? Material.LIME_DYE : Material.PAPER;
        String title = rank != null
                ? "#" + rank + " - " + option.displayName()
                : option.displayName();

        return createItem(material, title, lore);
    }

    private ItemStack buildSelectionSummaryItem(VoteSession session) {
        return createItem(
                Material.WRITABLE_BOOK,
                "Current Ranking",
                splitLines(ballotSummaryFormatter.formatSelectionSummary(session))
        );
    }

    private ItemStack buildConfirmationSummaryItem(VoteSession session) {
        return createItem(
                Material.BOOK,
                "Confirm Your Vote",
                splitLines(ballotSummaryFormatter.formatConfirmationSummary(session))
        );
    }

    private ItemStack buildResetItem() {
        return createItem(
                Material.BARRIER,
                "Reset Ranking",
                List.of(
                        "Clear all current selections",
                        "and start again."
                )
        );
    }

    private ItemStack buildCastItem(VoteSession session) {
        boolean valid = session.isValidSelection();

        List<String> lore = new ArrayList<>(splitLines(ballotSummaryFormatter.formatCastButtonSummary(session)));
        lore.add(" ");
        lore.add(valid ? "Click to continue to confirmation." : "Make a valid ranking to continue.");

        return createItem(
                valid ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                valid ? "Cast Your Vote" : "Cast Your Vote (Unavailable)",
                lore
        );
    }

    private ItemStack buildBackItem() {
        return createItem(
                Material.RED_CONCRETE,
                "Go Back",
                List.of(
                        "Return to the ranking screen",
                        "without submitting your vote."
                )
        );
    }

    private ItemStack buildCommitItem() {
        return createItem(
                Material.LIME_CONCRETE,
                "Yes, Commit My Vote",
                List.of(
                        "Submit this ranking",
                        "to the voting service."
                )
        );
    }

    private List<String> splitLines(String text) {
        Objects.requireNonNull(text, "text");
        return List.of(text.split("\\R"));
    }

    private String readablePollType(VoteSession session) {
        return switch (session.poll().pollType()) {
            case RANKED_SINGLE_WINNER -> "Ranked Choice";
            case YES_NO -> "Yes / No";
            case SINGLE_CHOICE -> "Single Choice";
            case RANKED_MULTI_WINNER_STV -> "STV";
            case COMBINED_EXECUTIVE_AND_COUNCIL -> "Executive + Council";
        };
    }

    private ItemStack createItem(Material material, String displayName, List<String> lore) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(lore, "lore");

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        return item;
    }
}