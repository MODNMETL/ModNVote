package com.modnmetl.modnvote.ui.builder.election;

import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderSession.Action;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderSession.Screen;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState.CandidateDraft;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState.DependencyDraft;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState.OfficeDraft;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the linked-offices builder GUI screens and binds each interactive slot
 * to an {@link Action} on the session. It contains no persistence or validation
 * logic of its own (validation results are computed by the service and recorded
 * on the session before rendering).
 */
public final class LinkedOfficesBuilderRenderer {

    private static final int SIZE = 54;
    private static final int LIST_MAX = 36;
    private static final int BACK_SLOT = 45;
    private static final int ACTION_SLOT = 48;
    private static final int CREATE_SLOT = 49;
    private static final int SAVE_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    public void open(Player player, LinkedOfficesBuilderSession session) {
        switch (session.getCurrentScreen()) {
            case MAIN -> openMain(player, session);
            case OFFICES -> openOffices(player, session);
            case OFFICE_EDITOR -> openOfficeEditor(player, session);
            case CANDIDATES -> openCandidates(player, session);
            case CANDIDATE_EDITOR -> openCandidateEditor(player, session);
            case DEPENDENCIES -> openDependencies(player, session);
        }
    }

    // --- MAIN ---------------------------------------------------------------

    private void openMain(Player player, LinkedOfficesBuilderSession session) {
        Inventory inv = newInventory(session, "Linked Offices Builder");
        LinkedOfficesBuilderState state = session.getState();

        List<String> info = new ArrayList<>();
        info.add("§7Poll: §f#" + session.getPollId());
        info.add("§7Offices: §f" + state.officeCount());
        info.add("§7Candidates: §f" + state.candidateCount());
        info.add("§7Dependencies: §f" + state.dependencyCount());
        info.add("§8");
        if (session.hasValidated()) {
            info.add(session.isLastValidationValid() ? "§aDefinition: VALID" : "§cDefinition: INVALID");
        } else {
            info.add("§7Definition: not yet validated");
        }
        set(inv, 4, item(Material.BOOK, "§6Linked Offices Definition", info));

        bind(inv, session, 20, item(Material.BEACON, "§eOffices",
                lore("§7View, create, edit, and delete offices.", "§8", "§7Count: §f" + state.officeCount())),
                new Action("NAV_OFFICES"));
        bind(inv, session, 22, item(Material.PLAYER_HEAD, "§eCandidates",
                lore("§7View, create, edit, and delete candidates.", "§8", "§7Count: §f" + state.candidateCount())),
                new Action("NAV_CANDIDATES"));
        bind(inv, session, 24, item(Material.CHAIN, "§eDependencies",
                lore("§7Manage EXCLUDE_WINNERS dependencies.", "§8", "§7Count: §f" + state.dependencyCount())),
                new Action("NAV_DEPENDENCIES"));

        bind(inv, session, ACTION_SLOT, item(Material.SPYGLASS, "§bValidate",
                buildValidationLore(session)), new Action("VALIDATE"));
        bind(inv, session, SAVE_SLOT, item(Material.LIME_WOOL, "§aSave",
                lore("§7Serialize and save through PollService.",
                        "§7Invalid definitions are rejected.",
                        "§8",
                        "§8Voting is not implemented yet.")), new Action("SAVE"));
        bind(inv, session, CLOSE_SLOT, item(Material.RED_WOOL, "§cClose",
                lore("§7Close the builder. Unsaved edits are discarded.")), new Action("CLOSE"));

        player.openInventory(inv);
    }

    // --- OFFICES ------------------------------------------------------------

    private void openOffices(Player player, LinkedOfficesBuilderSession session) {
        Inventory inv = newInventory(session, "Offices");
        List<OfficeDraft> offices = session.getState().offices();

        for (int i = 0; i < offices.size() && i < LIST_MAX; i++) {
            OfficeDraft office = offices.get(i);
            List<String> lore = lore(
                    "§7Display name: §f" + orUnset(office.displayName()),
                    "§7Method: §f" + orUnset(office.method() == null ? null : office.method().name()),
                    "§7Seats: §f" + office.seats(),
                    "§8",
                    "§eLeft-click: §7edit",
                    "§cRight-click: §7delete");
            bind(inv, session, i, item(Material.BEACON, "§b" + orUnset(office.officeKey()), lore),
                    new Action("OFFICE_ENTRY", office.officeKey()));
        }

        bind(inv, session, CREATE_SLOT, item(Material.EMERALD, "§aCreate Office",
                lore("§7Click to create a new office.")), new Action("CREATE_OFFICE"));
        navRow(inv, session, "NAV_MAIN");
        player.openInventory(inv);
    }

    // --- OFFICE EDITOR ------------------------------------------------------

    private void openOfficeEditor(Player player, LinkedOfficesBuilderSession session) {
        OfficeDraft office = session.getState().office(session.getEditingOfficeKey());
        if (office == null) {
            session.setCurrentScreen(Screen.OFFICES);
            openOffices(player, session);
            return;
        }

        Inventory inv = newInventory(session, "Edit Office: " + office.officeKey());

        bind(inv, session, 10, item(Material.NAME_TAG, "§eOffice key",
                lore("§7Current: §f" + office.officeKey(), "§8", "§7Click to rename.")),
                new Action("EDIT_OFFICE_KEY"));
        bind(inv, session, 11, item(Material.NAME_TAG, "§eDisplay name",
                lore("§7Current: §f" + orUnset(office.displayName()), "§8", "§7Click to edit.")),
                new Action("EDIT_OFFICE_NAME"));
        bind(inv, session, 12, item(Material.COMPARATOR, "§eCounting method",
                lore("§7Current: §f" + orUnset(office.method() == null ? null : office.method().name()),
                        "§8",
                        "§7Click to cycle IRV / APPROVAL_TOP_N.",
                        "§8IRV is always single-seat.")),
                new Action("CYCLE_METHOD"));
        bind(inv, session, 13, item(Material.TARGET, "§eSeats: §f" + office.seats(),
                lore("§eLeft-click: §7+1", "§cRight-click: §7-1",
                        office.method() == CountingMethod.IRV ? "§8IRV forces a single seat." : "§8")),
                new Action("SEAT_ADJUST"));
        String maxSel = office.maxSelections() == null ? "(unset)" : String.valueOf(office.maxSelections());
        bind(inv, session, 14, item(Material.HOPPER, "§eMax selections: §f" + maxSel,
                lore("§eLeft-click: §7+1", "§cRight-click: §7-1 (clears below 1)",
                        "§8Used by APPROVAL_TOP_N; defaults to seats if unset.")),
                new Action("MAXSEL_ADJUST"));
        bind(inv, session, 15, item(office.allowAbstain() ? Material.LIME_DYE : Material.GRAY_DYE,
                "§eAllow abstain: §f" + office.allowAbstain(),
                lore("§7Click to toggle.")), new Action("TOGGLE_ABSTAIN"));

        int standing = 0;
        for (CandidateDraft candidate : session.getState().candidates()) {
            if (candidate.eligibleOfficeKeys().contains(office.officeKey())) {
                standing++;
            }
        }
        set(inv, 16, item(Material.PAPER, "§7Candidates standing: §f" + standing,
                lore("§8Set candidate eligibility in the Candidates menu.")));

        bind(inv, session, ACTION_SLOT, item(Material.BARRIER, "§cDelete Office",
                lore("§7Removes this office and its dependencies.")),
                new Action("DELETE_OFFICE", office.officeKey()));
        navRow(inv, session, "NAV_OFFICES");
        player.openInventory(inv);
    }

    // --- CANDIDATES ---------------------------------------------------------

    private void openCandidates(Player player, LinkedOfficesBuilderSession session) {
        Inventory inv = newInventory(session, "Candidates");
        List<CandidateDraft> candidates = session.getState().candidates();

        for (int i = 0; i < candidates.size() && i < LIST_MAX; i++) {
            CandidateDraft candidate = candidates.get(i);
            List<String> lore = lore(
                    "§7Display name: §f" + orUnset(candidate.displayName()),
                    "§7Eligible offices: §f" + (candidate.eligibleOfficeKeys().isEmpty()
                            ? "(none)" : String.join(", ", candidate.eligibleOfficeKeys())),
                    "§8",
                    "§eLeft-click: §7edit",
                    "§cRight-click: §7delete");
            bind(inv, session, i, item(Material.PLAYER_HEAD, "§b" + orUnset(candidate.candidateKey()), lore),
                    new Action("CANDIDATE_ENTRY", candidate.candidateKey()));
        }

        bind(inv, session, CREATE_SLOT, item(Material.EMERALD, "§aCreate Candidate",
                lore("§7Click to create a new candidate.")), new Action("CREATE_CANDIDATE"));
        navRow(inv, session, "NAV_MAIN");
        player.openInventory(inv);
    }

    // --- CANDIDATE EDITOR ---------------------------------------------------

    private void openCandidateEditor(Player player, LinkedOfficesBuilderSession session) {
        CandidateDraft candidate = session.getState().candidate(session.getEditingCandidateKey());
        if (candidate == null) {
            session.setCurrentScreen(Screen.CANDIDATES);
            openCandidates(player, session);
            return;
        }

        Inventory inv = newInventory(session, "Edit Candidate: " + candidate.candidateKey());

        bind(inv, session, 10, item(Material.NAME_TAG, "§eCandidate key",
                lore("§7Current: §f" + candidate.candidateKey(), "§8", "§7Click to rename.")),
                new Action("EDIT_CANDIDATE_KEY"));
        bind(inv, session, 11, item(Material.NAME_TAG, "§eDisplay name",
                lore("§7Current: §f" + orUnset(candidate.displayName()), "§8", "§7Click to edit.")),
                new Action("EDIT_CANDIDATE_NAME"));

        set(inv, 13, item(Material.BOOK, "§eEligible offices",
                lore("§7Click an office below to toggle eligibility.",
                        "§8A candidate stands for the offices it is eligible for.")));

        List<OfficeDraft> offices = session.getState().offices();
        int slot = 18;
        for (int i = 0; i < offices.size() && slot < BACK_SLOT; i++, slot++) {
            OfficeDraft office = offices.get(i);
            boolean eligible = candidate.eligibleOfficeKeys().contains(office.officeKey());
            bind(inv, session, slot, item(eligible ? Material.LIME_WOOL : Material.GRAY_WOOL,
                    (eligible ? "§a" : "§7") + office.officeKey(),
                    lore(eligible ? "§aEligible — click to remove" : "§7Not eligible — click to add")),
                    new Action("TOGGLE_ELIGIBLE", office.officeKey()));
        }

        bind(inv, session, ACTION_SLOT, item(Material.BARRIER, "§cDelete Candidate",
                lore("§7Removes this candidate from the definition.")),
                new Action("DELETE_CANDIDATE", candidate.candidateKey()));
        navRow(inv, session, "NAV_CANDIDATES");
        player.openInventory(inv);
    }

    // --- DEPENDENCIES -------------------------------------------------------

    private void openDependencies(Player player, LinkedOfficesBuilderSession session) {
        if (session.isAddingDependency()) {
            openDependencyPicker(player, session);
            return;
        }

        Inventory inv = newInventory(session, "Dependencies");
        List<DependencyDraft> dependencies = session.getState().dependencies();

        for (int i = 0; i < dependencies.size() && i < LIST_MAX; i++) {
            DependencyDraft dep = dependencies.get(i);
            List<String> lore = lore(
                    "§7Type: §f" + dep.type().name(),
                    "§7From office: §f" + dep.fromOfficeKey(),
                    "§7Target office: §f" + dep.appliesToOfficeKey(),
                    "§8",
                    "§cRight-click: §7delete");
            bind(inv, session, i, item(Material.CHAIN,
                    "§b" + dep.fromOfficeKey() + " §7-> §b" + dep.appliesToOfficeKey(), lore),
                    new Action("DEPENDENCY_ENTRY", String.valueOf(i)));
        }

        bind(inv, session, CREATE_SLOT, item(Material.EMERALD, "§aAdd Dependency",
                lore("§7Excludes winners of one office from another.")), new Action("ADD_DEPENDENCY"));
        navRow(inv, session, "NAV_MAIN");
        player.openInventory(inv);
    }

    private void openDependencyPicker(Player player, LinkedOfficesBuilderSession session) {
        boolean pickingFrom = session.getPendingDependencyFrom() == null;
        Inventory inv = newInventory(session, pickingFrom ? "Pick FROM office" : "Pick TARGET office");

        List<OfficeDraft> offices = session.getState().offices();
        for (int i = 0; i < offices.size() && i < LIST_MAX; i++) {
            OfficeDraft office = offices.get(i);
            String action = pickingFrom ? "DEP_PICK_FROM" : "DEP_PICK_TARGET";
            String hint = pickingFrom
                    ? "§7Winners of this office will be excluded elsewhere."
                    : "§7This office will exclude the chosen winners.";
            bind(inv, session, i, item(Material.BEACON, "§b" + office.officeKey(),
                    lore(hint)), new Action(action, office.officeKey()));
        }

        bind(inv, session, BACK_SLOT, item(Material.ARROW, "§7Cancel",
                lore("§7Cancel adding a dependency.")), new Action("DEP_CANCEL_ADD"));
        bind(inv, session, CLOSE_SLOT, item(Material.RED_WOOL, "§cClose",
                lore("§7Close the builder.")), new Action("CLOSE"));
        player.openInventory(inv);
    }

    // --- helpers ------------------------------------------------------------

    private Inventory newInventory(LinkedOfficesBuilderSession session, String title) {
        session.clearSlotActions();
        return Bukkit.createInventory(new LinkedOfficesBuilderHolder(session), SIZE, title);
    }

    private void navRow(Inventory inv, LinkedOfficesBuilderSession session, String backAction) {
        bind(inv, session, BACK_SLOT, item(Material.ARROW, "§7Back",
                lore("§7Return to the previous menu.")), new Action(backAction));
        bind(inv, session, CLOSE_SLOT, item(Material.RED_WOOL, "§cClose",
                lore("§7Close the builder. Unsaved edits are discarded.")), new Action("CLOSE"));
    }

    private List<String> buildValidationLore(LinkedOfficesBuilderSession session) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Validate via ElectionDefinitionService.");
        lore.add("§8");
        if (!session.hasValidated()) {
            lore.add("§7Click to validate.");
            return lore;
        }
        if (session.isLastValidationValid()) {
            lore.add("§aVALID");
            return lore;
        }
        lore.add("§cINVALID:");
        int shown = 0;
        for (String issue : session.getLastValidationIssues()) {
            if (shown >= 6) {
                lore.add("§7...and " + (session.getLastValidationIssues().size() - shown) + " more.");
                break;
            }
            lore.add("§c- " + issue);
            shown++;
        }
        return lore;
    }

    private void bind(Inventory inv, LinkedOfficesBuilderSession session, int slot, ItemStack item, Action action) {
        inv.setItem(slot, item);
        session.bindAction(slot, action);
    }

    private void set(Inventory inv, int slot, ItemStack item) {
        inv.setItem(slot, item);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<String> lore(String... lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(line);
        }
        return out;
    }

    private String orUnset(String value) {
        return value == null || value.isBlank() ? "§8(unset)" : value;
    }
}
