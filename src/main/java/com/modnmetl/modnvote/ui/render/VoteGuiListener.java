package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.ui.session.VoteSession;
import com.modnmetl.modnvote.ui.session.VoteSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Handles interaction safety and local session-state transitions for ModNVote GUIs.
 *
 * This first listener phase supports:
 * - blocking unsafe inventory interactions
 * - ranked option selection/removal
 * - reset button
 * - move to confirmation
 * - back from confirmation
 *
 * Ballot submission is intentionally not handled here yet.
 */
public final class VoteGuiListener implements Listener {

    private final VoteSessionManager voteSessionManager;
    private final JavaInventoryVoteRenderer voteRenderer;

    public VoteGuiListener(VoteSessionManager voteSessionManager,
                           JavaInventoryVoteRenderer voteRenderer) {
        this.voteSessionManager = Objects.requireNonNull(voteSessionManager, "voteSessionManager");
        this.voteRenderer = Objects.requireNonNull(voteRenderer, "voteRenderer");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!voteRenderer.isManagedInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ModNVoteInventoryHolder holder = voteRenderer.requireManagedHolder(event.getView().getTopInventory());

        Optional<VoteSession> optionalSession =
                voteSessionManager.findSession(player.getUniqueId(), holder.pollId());

        if (optionalSession.isEmpty()) {
            player.closeInventory();
            return;
        }

        VoteSession session = optionalSession.get();

        if (!session.playerUuid().equals(holder.playerUuid())) {
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();

        if (session.isInSelectionScreen()) {
            handleSelectionClick(player, session, rawSlot);
            return;
        }

        if (session.isInConfirmationScreen()) {
            handleConfirmationClick(player, session, rawSlot);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!voteRenderer.isManagedInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
    }

    private void handleSelectionClick(Player player, VoteSession session, int rawSlot) {
        if (voteRenderer.isSelectionOptionSlot(rawSlot)) {
            int optionIndex = voteRenderer.optionIndexForSlot(rawSlot);
            if (optionIndex >= 0 && optionIndex < session.options().size()) {
                long optionId = session.options().get(optionIndex).optionId();
                session.toggleRankedSelection(optionId);
                voteRenderer.refresh(player, session);
            }
            return;
        }

        if (voteRenderer.isResetSlot(rawSlot)) {
            session.clearSelections();
            voteRenderer.refresh(player, session);
            return;
        }

        if (voteRenderer.isCastSlot(rawSlot) && session.isValidSelection()) {
            session.moveToConfirmation();
            voteRenderer.refresh(player, session);
        }
    }

    private void handleConfirmationClick(Player player, VoteSession session, int rawSlot) {
        if (voteRenderer.isConfirmationBackSlot(rawSlot)) {
            session.returnToSelection();
            voteRenderer.refresh(player, session);
            return;
        }

        if (voteRenderer.isConfirmationCommitSlot(rawSlot)) {
            // Submission wiring intentionally deferred to the next phase.
        }
    }
}