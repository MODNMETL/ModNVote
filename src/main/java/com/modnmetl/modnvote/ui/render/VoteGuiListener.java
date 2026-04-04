package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.ui.session.VoteSession;
import com.modnmetl.modnvote.ui.session.VoteSessionManager;
import com.modnmetl.modnvote.ui.submit.VoteSubmissionCoordinator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles interaction safety and local session-state transitions for ModNVote GUIs.
 *
 * This listener now supports:
 * - blocking unsafe inventory interactions
 * - ranked option selection/removal
 * - reset button
 * - move to confirmation
 * - back from confirmation
 * - confirmed ballot submission through the submission coordinator
 */
public final class VoteGuiListener implements Listener {

    private final VoteSessionManager voteSessionManager;
    private final JavaInventoryVoteRenderer voteRenderer;
    private final VoteSubmissionCoordinator voteSubmissionCoordinator;
    private final MessageService messages;

    public VoteGuiListener(VoteSessionManager voteSessionManager,
                           JavaInventoryVoteRenderer voteRenderer,
                           VoteSubmissionCoordinator voteSubmissionCoordinator,
                           MessageService messages) {
        this.voteSessionManager = Objects.requireNonNull(voteSessionManager, "voteSessionManager");
        this.voteRenderer = Objects.requireNonNull(voteRenderer, "voteRenderer");
        this.voteSubmissionCoordinator = Objects.requireNonNull(voteSubmissionCoordinator, "voteSubmissionCoordinator");
        this.messages = Objects.requireNonNull(messages, "messages");
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

        if (!voteRenderer.holderMatchesSessionScreen(holder, session)) {
            voteRenderer.refresh(player, session);
            return;
        }

        int rawSlot = event.getRawSlot();

        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

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
            submitConfirmedVote(player, session);
        }
    }

    private void submitConfirmedVote(Player player, VoteSession session) {
        try {
            VoteSubmissionCoordinator.SubmissionOutcome outcome =
                    voteSubmissionCoordinator.submitRankedVote(player, session);

            voteSessionManager.removeSession(player.getUniqueId(), session.pollId());
            player.closeInventory();

            player.sendMessage(messages.get("vote.submit_success"));
            player.sendMessage(messages.get("vote.education_privacy"));
            player.sendMessage(messages.get("vote.education_verification"));
            player.sendMessage(messages.formatRaw(
                    "vote.ballot_hash",
                    Map.of("ballot_hash", outcome.submissionResult().ballotHash())
            ));
            player.sendMessage(messages.formatRaw(
                    "vote.receipt_hash",
                    Map.of("receipt_hash", outcome.submissionResult().receiptHash())
            ));

            if (outcome.bypassIpDuplicateCheck()) {
                player.sendMessage(messages.get("vote.bypass_used"));
            }
        } catch (PollServiceException e) {
            player.sendMessage(messages.format(
                    "errors.vote_failed",
                    Map.of("reason", e.getMessage())
            ));
            voteRenderer.refresh(player, session);
        }
    }
}