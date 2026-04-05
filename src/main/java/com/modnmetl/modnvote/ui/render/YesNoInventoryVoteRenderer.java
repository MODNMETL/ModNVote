package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.ui.session.VoteScreen;
import com.modnmetl.modnvote.ui.session.YesNoVoteSession;
import com.modnmetl.modnvote.ui.text.YesNoGuiText;
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
 * Java inventory renderer for yes/no vote sessions.
 */
public final class YesNoInventoryVoteRenderer {

    private static final int SELECTION_SIZE = 27;
    private static final int CONFIRMATION_SIZE = 27;

    private static final int INFO_SLOT = 4;
    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;
    private static final int SUMMARY_SLOT = 13;
    private static final int CLEAR_SLOT = 18;
    private static final int CAST_SLOT = 26;

    private static final int CONFIRM_SUMMARY_SLOT = 13;
    private static final int CONFIRM_BACK_SLOT = 11;
    private static final int CONFIRM_COMMIT_SLOT = 15;

    private final JavaPlugin plugin;
    private final YesNoGuiText yesNoGuiText;
    private final Set<UUID> playersWithManagedReopenInProgress = ConcurrentHashMap.newKeySet();

    public YesNoInventoryVoteRenderer(JavaPlugin plugin,
                                      YesNoGuiText yesNoGuiText) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.yesNoGuiText = Objects.requireNonNull(yesNoGuiText, "yesNoGuiText");
    }

    public void openSelection(Player player, YesNoVoteSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        Inventory inventory = Bukkit.createInventory(
                new ModNVoteInventoryHolder(
                        player.getUniqueId(),
                        session.pollId(),
                        VoteScreen.SELECTION,
                        VoteUiFlow.YES_NO
                ),
                SELECTION_SIZE,
                buildSelectionTitle(session)
        );

        populateSelectionInventory(inventory, session);
        openManagedInventory(player, inventory);
    }

    public void openConfirmation(Player player, YesNoVoteSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        Inventory inventory = Bukkit.createInventory(
                new ModNVoteInventoryHolder(
                        player.getUniqueId(),
                        session.pollId(),
                        VoteScreen.CONFIRMATION,
                        VoteUiFlow.YES_NO
                ),
                CONFIRMATION_SIZE,
                buildConfirmationTitle(session)
        );

        populateConfirmationInventory(inventory, session);
        openManagedInventory(player, inventory);
    }

    public void refresh(Player player, YesNoVoteSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        if (session.isInConfirmationScreen()) {
            openConfirmation(player, session);
            return;
        }

        openSelection(player, session);
    }

    public Optional<Long> optionIdAtSlot(YesNoVoteSession session, int rawSlot) {
        Objects.requireNonNull(session, "session");

        List<PollOption> options = session.options();
        if (options.size() != 2) {
            return Optional.empty();
        }

        if (rawSlot == YES_SLOT) {
            return Optional.of(options.get(0).optionId());
        }
        if (rawSlot == NO_SLOT) {
            return Optional.of(options.get(1).optionId());
        }

        return Optional.empty();
    }

    public boolean isClearSlot(int rawSlot) {
        return rawSlot == CLEAR_SLOT;
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
        return inventory != null
                && inventory.getHolder() instanceof ModNVoteInventoryHolder holder
                && holder.uiFlow() == VoteUiFlow.YES_NO;
    }

    public ModNVoteInventoryHolder requireManagedHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof ModNVoteInventoryHolder holder)
                || holder.uiFlow() != VoteUiFlow.YES_NO) {
            throw new IllegalArgumentException("Inventory is not managed by yes/no ModNVote UI.");
        }
        return holder;
    }

    public boolean holderMatchesSessionScreen(ModNVoteInventoryHolder holder, YesNoVoteSession session) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(session, "session");
        return holder.screen() == session.currentScreen() && holder.uiFlow() == VoteUiFlow.YES_NO;
    }

    public boolean isManagedReopenInProgress(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return playersWithManagedReopenInProgress.contains(playerUuid);
    }

    private String buildSelectionTitle(YesNoVoteSession session) {
        return truncateTitle(yesNoGuiText.selectionTitle(session));
    }

    private String buildConfirmationTitle(YesNoVoteSession session) {
        return truncateTitle(yesNoGuiText.confirmationTitle(session));
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

        clearCursor(player);
        player.openInventory(inventory);

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    clearCursor(player);
                    playersWithManagedReopenInProgress.remove(playerUuid);
                },
                1L
        );
    }

    private void clearCursor(Player player) {
        player.setItemOnCursor(new ItemStack(Material.AIR));
    }

    private void populateSelectionInventory(Inventory inventory, YesNoVoteSession session) {
        // Intentionally leave background slots empty for no-pane visual testing.

        inventory.setItem(INFO_SLOT, buildPollInfoItem(session));
        inventory.setItem(SUMMARY_SLOT, buildSummaryItem(session));
        inventory.setItem(CLEAR_SLOT, buildClearItem());
        inventory.setItem(CAST_SLOT, buildCastItem(session));

        List<PollOption> options = session.options();
        if (options.size() == 2) {
            inventory.setItem(YES_SLOT, buildOptionItem(session, options.get(0), Material.LIME_CONCRETE));
            inventory.setItem(NO_SLOT, buildOptionItem(session, options.get(1), Material.RED_CONCRETE));
        }
    }

    private void populateConfirmationInventory(Inventory inventory, YesNoVoteSession session) {
        // Intentionally leave background slots empty for no-pane visual testing.

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

    private ItemStack buildPollInfoItem(YesNoVoteSession session) {
        YesNoGuiText.ItemText text = yesNoGuiText.pollInfo(session);
        return createItem(Material.BOOK, text.title(), text.lore());
    }

    private ItemStack buildOptionItem(YesNoVoteSession session, PollOption option, Material baseMaterial) {
        YesNoGuiText.ItemText text = yesNoGuiText.option(session, option);
        Material material = session.isSelected(option.optionId()) ? Material.GLOWSTONE_DUST : baseMaterial;
        return createItem(material, text.title(), text.lore());
    }

    private ItemStack buildSummaryItem(YesNoVoteSession session) {
        YesNoGuiText.ItemText text = yesNoGuiText.summary(session);
        return createItem(Material.WRITABLE_BOOK, text.title(), text.lore());
    }

    private ItemStack buildConfirmationSummaryItem(YesNoVoteSession session) {
        YesNoGuiText.ItemText text = yesNoGuiText.confirmationSummary(session);
        return createItem(Material.BOOK, text.title(), text.lore());
    }

    private ItemStack buildClearItem() {
        YesNoGuiText.ItemText text = yesNoGuiText.clearButton();
        return createItem(Material.BARRIER, text.title(), text.lore());
    }

    private ItemStack buildCastItem(YesNoVoteSession session) {
        YesNoGuiText.ItemText text = yesNoGuiText.reviewButton(session);
        return createItem(
                session.isValidSelection() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                text.title(),
                text.lore()
        );
    }

    private ItemStack buildBackItem() {
        YesNoGuiText.ItemText text = yesNoGuiText.backButton();
        return createItem(Material.RED_CONCRETE, text.title(), text.lore());
    }

    private ItemStack buildCommitItem() {
        YesNoGuiText.ItemText text = yesNoGuiText.commitButton();
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