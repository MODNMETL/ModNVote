package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.ui.session.VoteScreen;
import com.modnmetl.modnvote.ui.session.VoteSession;
import com.modnmetl.modnvote.ui.text.VoteGuiText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java inventory renderer for ranked voting sessions.
 *
 * Responsibilities:
 * - build selection and confirmation inventories
 * - render option, summary, and control items
 * - own slot mapping for ranked option rendering
 * - track managed reopen transitions so session cleanup listeners can
 *   distinguish renderer refreshes from genuine user closure
 *
 * Non-responsibilities:
 * - no click handling
 * - no session persistence
 * - no ballot submission logic
 */
public final class JavaInventoryVoteRenderer implements VoteRenderer {

    private static final int SELECTION_SIZE = 54;
    private static final int CONFIRMATION_SIZE = 27;

    private static final int INFO_SLOT = 4;
    private static final int SUMMARY_SLOT = 49;
    private static final int RESET_SLOT = 45;
    private static final int CAST_SLOT = 53;

    private static final int CONFIRM_SUMMARY_SLOT = 13;
    private static final int CONFIRM_BACK_SLOT = 11;
    private static final int CONFIRM_COMMIT_SLOT = 15;

    /**
     * Fixed option grid for ranked voting.
     * This currently supports up to 21 visible options in a stable layout.
     */
    private static final int[] OPTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final JavaPlugin plugin;
    private final VoteGuiText voteGuiText;

    /**
     * Tracks players whose vote GUI is being intentionally reopened by the plugin.
     *
     * This prevents InventoryCloseEvent cleanup logic from treating refresh/open
     * transitions as if the player manually abandoned the session.
     */
    private final Set<UUID> playersWithManagedReopenInProgress = ConcurrentHashMap.newKeySet();

    public JavaInventoryVoteRenderer(JavaPlugin plugin,
                                     VoteGuiText voteGuiText) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.voteGuiText = Objects.requireNonNull(voteGuiText, "voteGuiText");
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
        openManagedInventory(player, inventory);
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
        openManagedInventory(player, inventory);
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

    public Optional<Long> selectionOptionIdAtSlot(VoteSession session, int rawSlot) {
        Objects.requireNonNull(session, "session");

        int optionIndex = optionIndexForSlot(rawSlot);
        if (optionIndex < 0 || optionIndex >= session.options().size()) {
            return Optional.empty();
        }

        return Optional.of(session.options().get(optionIndex).optionId());
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
        return inventory != null && inventory.getHolder() instanceof ModNVoteInventoryHolder;
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

    public boolean isManagedReopenInProgress(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return playersWithManagedReopenInProgress.contains(playerUuid);
    }

    private int optionIndexForSlot(int rawSlot) {
        for (int i = 0; i < OPTION_SLOTS.length; i++) {
            if (OPTION_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    private String buildSelectionTitle(VoteSession session) {
        return truncateTitle(voteGuiText.selectionTitle(session));
    }

    private String buildConfirmationTitle(VoteSession session) {
        return truncateTitle(voteGuiText.confirmationTitle(session));
    }

    private String truncateTitle(String rawTitle) {
        Objects.requireNonNull(rawTitle, "rawTitle");

        final int maxLength = 32;
        if (rawTitle.length() <= maxLength) {
            return rawTitle;
        }

        return rawTitle.substring(0, maxLength - 3) + "...";
    }

    private void openManagedInventory(Player player, Inventory inventory) {
        UUID playerUuid = player.getUniqueId();
        playersWithManagedReopenInProgress.add(playerUuid);
        player.openInventory(inventory);

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> playersWithManagedReopenInProgress.remove(playerUuid),
                1L
        );
    }

    private void populateSelectionInventory(Inventory inventory, VoteSession session) {
        fillInventory(inventory, createFillerPane());

        inventory.setItem(INFO_SLOT, buildPollInfoItem(session));
        inventory.setItem(SUMMARY_SLOT, buildSelectionSummaryItem(session));
        inventory.setItem(RESET_SLOT, buildResetItem());
        inventory.setItem(CAST_SLOT, buildCastItem(session));

        int renderCount = Math.min(session.options().size(), OPTION_SLOTS.length);

        for (int i = 0; i < renderCount; i++) {
            PollOption option = session.options().get(i);
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
        VoteGuiText.ItemText text = voteGuiText.pollInfo(session);
        return createItem(Material.BOOK, text.title(), text.lore());
    }

    private ItemStack buildOptionItem(VoteSession session, PollOption option) {
        Integer rank = session.assignedRank(option.optionId());
        VoteGuiText.ItemText text = voteGuiText.option(session, option);

        Material material;
        if (rank != null) {
            material = Material.LIME_DYE;
        } else if (session.canAssignAnotherRank()) {
            material = Material.PAPER;
        } else {
            material = Material.GRAY_DYE;
        }

        return createItem(material, text.title(), text.lore());
    }

    private ItemStack buildSelectionSummaryItem(VoteSession session) {
        VoteGuiText.ItemText text = voteGuiText.selectionSummary(session);
        return createItem(Material.WRITABLE_BOOK, text.title(), text.lore());
    }

    private ItemStack buildConfirmationSummaryItem(VoteSession session) {
        VoteGuiText.ItemText text = voteGuiText.confirmationSummary(session);
        return createItem(Material.BOOK, text.title(), text.lore());
    }

    private ItemStack buildResetItem() {
        VoteGuiText.ItemText text = voteGuiText.resetButton();
        return createItem(Material.BARRIER, text.title(), text.lore());
    }

    private ItemStack buildCastItem(VoteSession session) {
        VoteGuiText.ItemText text = voteGuiText.reviewButton(session);
        return createItem(
                session.isValidSelection() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                text.title(),
                text.lore()
        );
    }

    private ItemStack buildBackItem() {
        VoteGuiText.ItemText text = voteGuiText.backButton();
        return createItem(Material.RED_CONCRETE, text.title(), text.lore());
    }

    private ItemStack buildCommitItem() {
        VoteGuiText.ItemText text = voteGuiText.commitButton();
        return createItem(Material.LIME_CONCRETE, text.title(), text.lore());
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