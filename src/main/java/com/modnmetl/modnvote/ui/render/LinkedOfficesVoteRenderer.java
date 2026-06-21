package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.domain.election.CandidateDefinition;
import com.modnmetl.modnvote.domain.election.ContestDefinition;
import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.platform.ModNScheduler;
import com.modnmetl.modnvote.ui.session.election.LinkedOfficesVoteScreen;
import com.modnmetl.modnvote.ui.session.election.LinkedOfficesVoteSession;
import com.modnmetl.modnvote.ui.session.election.LinkedOfficesVoteState;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java inventory renderer for linked-offices vote sessions.
 *
 * <p>Three screens share a 54-slot chest layout: an office list (OVERVIEW), a
 * per-office candidate screen (OFFICE), and a final summary (REVIEW). Content
 * items occupy slots {@value #CONTENT_START}..{@value #CONTENT_END}; the bottom
 * row carries the navigation/action buttons. This class only renders and maps
 * slots — all ballot state lives in the Bukkit-free
 * {@link LinkedOfficesVoteState}.
 */
public final class LinkedOfficesVoteRenderer {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;
    private static final int CONTENT_CAPACITY = CONTENT_END - CONTENT_START + 1;

    private static final int BACK_SLOT = 45;
    private static final int CLEAR_SLOT = 49;
    private static final int ACTION_SLOT = 53;

    private final ModNScheduler scheduler;
    private final Set<UUID> playersWithManagedReopenInProgress = ConcurrentHashMap.newKeySet();

    public LinkedOfficesVoteRenderer(ModNScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void open(Player player, LinkedOfficesVoteSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        switch (session.currentScreen()) {
            case OVERVIEW -> openOverview(player, session);
            case OFFICE -> openOffice(player, session);
            case REVIEW -> openReview(player, session);
        }
    }

    public void refresh(Player player, LinkedOfficesVoteSession session) {
        open(player, session);
    }

    public void openOverview(Player player, LinkedOfficesVoteSession session) {
        Inventory inventory = createInventory(player, session, LinkedOfficesVoteScreen.OVERVIEW, null,
                "Vote: " + session.poll().title());

        LinkedOfficesVoteState state = session.state();
        List<ContestDefinition> contests = state.contests();
        inventory.setItem(INFO_SLOT, createItem(Material.BOOK, "§6" + session.poll().title(),
                List.of("§7Click an office to cast your", "§7preferences, then review and submit.")));

        for (int i = 0; i < contests.size() && i < CONTENT_CAPACITY; i++) {
            ContestDefinition contest = contests.get(i);
            boolean addressed = state.officeAddressed(contest.officeKey());
            List<String> lore = new ArrayList<>();
            lore.add("§7Method: §f" + methodLabel(contest.method()));
            lore.add("§7Seats: §f" + contest.seats());
            lore.add("§7Your selections: §f" + state.selectionCount(contest.officeKey()));
            lore.add(addressed ? "§aReady" : "§cNeeds your input");
            lore.add("§eClick to open this office");
            inventory.setItem(CONTENT_START + i,
                    createItem(addressed ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                            "§f" + officeDisplay(contest), lore));
        }

        inventory.setItem(ACTION_SLOT, buildActionItem(state.isSubmittable(), "§aReview & Submit",
                "§7Open the review screen to", "§7check and submit your ballot."));
        openManagedInventory(player, inventory);
    }

    public void openOffice(Player player, LinkedOfficesVoteSession session) {
        String officeKey = session.currentOfficeKey();
        LinkedOfficesVoteState state = session.state();
        ContestDefinition contest = state.requireContest(officeKey);

        Inventory inventory = createInventory(player, session, LinkedOfficesVoteScreen.OFFICE, officeKey,
                "Office: " + officeDisplay(contest));

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Method: §f" + methodLabel(contest.method()));
        infoLore.add("§7Seats: §f" + contest.seats());
        if (isRankedMethod(contest.method())) {
            infoLore.add("§7Click candidates in order of preference.");
        } else {
            Integer max = state.maxSelections(officeKey);
            infoLore.add("§7Approve up to §f" + (max == null ? "all" : max) + " §7candidates.");
            infoLore.add("§7Selected: §f" + state.selectionCount(officeKey)
                    + (max == null ? "" : " §7/ §f" + max));
        }
        inventory.setItem(INFO_SLOT, createItem(Material.BOOK, "§6" + officeDisplay(contest), infoLore));

        List<String> eligible = state.eligibleCandidates(officeKey);
        boolean ranked = isRankedMethod(contest.method());
        for (int i = 0; i < eligible.size() && i < CONTENT_CAPACITY; i++) {
            String candidateKey = eligible.get(i);
            boolean selected = state.isSelected(officeKey, candidateKey);
            List<String> lore = new ArrayList<>();
            if (selected) {
                if (ranked) {
                    lore.add("§aRanked #" + state.rankOf(officeKey, candidateKey));
                } else {
                    lore.add("§aApproved");
                }
                lore.add("§7Click to remove");
            } else {
                lore.add(ranked ? "§7Click to add to your ranking" : "§7Click to approve");
            }
            inventory.setItem(CONTENT_START + i,
                    createItem(selected ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                            "§f" + candidateDisplay(state, candidateKey), lore));
        }

        inventory.setItem(BACK_SLOT, createItem(Material.ARROW, "§eBack to offices",
                List.of("§7Return to the office list")));
        inventory.setItem(CLEAR_SLOT, createItem(Material.BARRIER, "§cClear this office",
                List.of("§7Remove all selections for", "§7this office")));
        openManagedInventory(player, inventory);
    }

    public void openReview(Player player, LinkedOfficesVoteSession session) {
        LinkedOfficesVoteState state = session.state();
        Inventory inventory = createInventory(player, session, LinkedOfficesVoteScreen.REVIEW, null,
                "Review: " + session.poll().title());

        inventory.setItem(INFO_SLOT, createItem(Material.BOOK, "§6Review your ballot",
                List.of("§7Check your selections, then submit.",
                        "§7A private proof phrase will be shown.")));

        List<ContestDefinition> contests = state.contests();
        for (int i = 0; i < contests.size() && i < CONTENT_CAPACITY; i++) {
            ContestDefinition contest = contests.get(i);
            List<String> selections = state.selectionsFor(contest.officeKey());
            List<String> lore = new ArrayList<>();
            lore.add("§7Method: §f" + methodLabel(contest.method()));
            if (selections.isEmpty()) {
                lore.add(contest.allowAbstain() ? "§7Abstained" : "§cNo selection");
            } else if (isRankedMethod(contest.method())) {
                int rank = 1;
                for (String key : selections) {
                    lore.add("§7#" + rank++ + " §f" + candidateDisplay(state, key));
                }
            } else {
                for (String key : selections) {
                    lore.add("§a• §f" + candidateDisplay(state, key));
                }
            }
            inventory.setItem(CONTENT_START + i,
                    createItem(Material.PAPER, "§f" + officeDisplay(contest), lore));
        }

        inventory.setItem(BACK_SLOT, createItem(Material.ARROW, "§eBack to offices",
                List.of("§7Return to the office list")));
        inventory.setItem(ACTION_SLOT, buildActionItem(state.isSubmittable(), "§aSubmit ballot",
                "§7Submit your anonymous ballot.", "§7This cannot be changed afterwards."));
        openManagedInventory(player, inventory);
    }

    // --- slot resolution (used by the listener) -------------------------------

    public Optional<String> officeKeyAtSlot(LinkedOfficesVoteSession session, int rawSlot) {
        if (rawSlot < CONTENT_START || rawSlot > CONTENT_END) {
            return Optional.empty();
        }
        int index = rawSlot - CONTENT_START;
        List<ContestDefinition> contests = session.state().contests();
        if (index < 0 || index >= contests.size()) {
            return Optional.empty();
        }
        return Optional.of(contests.get(index).officeKey());
    }

    public Optional<String> candidateKeyAtSlot(LinkedOfficesVoteSession session, int rawSlot) {
        String officeKey = session.currentOfficeKey();
        if (officeKey == null || rawSlot < CONTENT_START || rawSlot > CONTENT_END) {
            return Optional.empty();
        }
        int index = rawSlot - CONTENT_START;
        List<String> eligible = session.state().eligibleCandidates(officeKey);
        if (index < 0 || index >= eligible.size()) {
            return Optional.empty();
        }
        return Optional.of(eligible.get(index));
    }

    public boolean isBackSlot(int rawSlot) {
        return rawSlot == BACK_SLOT;
    }

    public boolean isClearSlot(int rawSlot) {
        return rawSlot == CLEAR_SLOT;
    }

    public boolean isActionSlot(int rawSlot) {
        return rawSlot == ACTION_SLOT;
    }

    public boolean isManagedInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof LinkedOfficesInventoryHolder;
    }

    public LinkedOfficesInventoryHolder requireManagedHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof LinkedOfficesInventoryHolder holder)) {
            throw new IllegalArgumentException("Inventory is not managed by linked-offices ModNVote UI.");
        }
        return holder;
    }

    public boolean holderMatchesSessionScreen(LinkedOfficesInventoryHolder holder, LinkedOfficesVoteSession session) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(session, "session");
        if (holder.screen() != session.currentScreen()) {
            return false;
        }
        if (holder.screen() == LinkedOfficesVoteScreen.OFFICE) {
            return Objects.equals(holder.officeKey(), session.currentOfficeKey());
        }
        return true;
    }

    public boolean isManagedReopenInProgress(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return playersWithManagedReopenInProgress.contains(playerUuid);
    }

    // --- helpers --------------------------------------------------------------

    private Inventory createInventory(Player player,
                                      LinkedOfficesVoteSession session,
                                      LinkedOfficesVoteScreen screen,
                                      String officeKey,
                                      String rawTitle) {
        return Bukkit.createInventory(
                new LinkedOfficesInventoryHolder(player.getUniqueId(), session.pollId(), screen, officeKey),
                SIZE,
                truncateTitle(rawTitle));
    }

    private ItemStack buildActionItem(boolean enabled, String title, String... loreLines) {
        List<String> lore = new ArrayList<>(List.of(loreLines));
        if (!enabled) {
            lore.add("§cComplete every required office first.");
        }
        return createItem(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE, title, lore);
    }

    /**
     * Human-readable label for an office's counting method, shown on the overview,
     * office, and review screens. Delegates to the Bukkit-free
     * {@link LinkedOfficesVoteMethodText} (unit-tested there) so STV is labelled
     * "Ranked (STV)", never Approval.
     */
    private String methodLabel(CountingMethod method) {
        return LinkedOfficesVoteMethodText.methodLabel(method);
    }

    /**
     * Whether an office is rendered and operated as a ranked contest. Delegates to
     * {@link LinkedOfficesVoteMethodText#isRankedMethod(CountingMethod)} so IRV and
     * STV both render as ranked.
     */
    private boolean isRankedMethod(CountingMethod method) {
        return LinkedOfficesVoteMethodText.isRankedMethod(method);
    }

    private String officeDisplay(ContestDefinition contest) {
        return contest.displayName() == null || contest.displayName().isBlank()
                ? contest.officeKey()
                : contest.displayName();
    }

    private String candidateDisplay(LinkedOfficesVoteState state, String candidateKey) {
        return state.definition().findCandidate(candidateKey)
                .map(CandidateDefinition::displayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(candidateKey);
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
        scheduler.runForPlayerLater(player, () -> {
            clearCursor(player);
            playersWithManagedReopenInProgress.remove(playerUuid);
        }, 1L);
    }

    private void clearCursor(Player player) {
        player.setItemOnCursor(new ItemStack(Material.AIR));
    }

    private ItemStack createItem(Material material, String displayName, List<String> lore) {
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
