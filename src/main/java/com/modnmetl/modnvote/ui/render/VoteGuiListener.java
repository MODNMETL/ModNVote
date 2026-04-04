package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.ui.feedback.VoteSoundService;
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
 * Responsibilities:
 * - block unsafe inventory interactions
 * - resolve managed sessions for click events
 * - perform local ranked-selection state transitions
 * - delegate confirmed submission through the submission coordinator
 * - trigger optional UI feedback sounds
 *
 * Non-responsibilities:
 * - no direct database writes
 * - no authoritative ballot validation
 * - no renderer layout ownership beyond using renderer-provided slot resolution
 */
public final class VoteGuiListener implements Listener {

    private final VoteSessionManager voteSessionManager;
    private final JavaInventoryVoteRenderer voteRenderer;
    private final VoteSubmissionCoordinator voteSubmissionCoordinator;
    private final VoteSoundService voteSoundService;
    private final MessageService messages;

    public VoteGuiListener(VoteSessionManager voteSessionManager,
                           JavaInventoryVoteRenderer voteRenderer,
                           VoteSubmissionCoordinator voteSubmissionCoordinator,
                           VoteSoundService voteSoundService,
                           MessageService messages) {
        this.voteSessionManager = Objects.requireNonNull(voteSessionManager, "voteSessionManager");
        this.voteRenderer = Objects.requireNonNull(voteRenderer, "voteRenderer");
        this.voteSubmissionCoordinator = Objects.requireNonNull(voteSubmissionCoordinator, "voteSubmissionCoordinator");
        this.voteSoundService = Objects.requireNonNull(voteSoundService, "voteSoundService");
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

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        SessionResolution resolution = resolveSession(player, event);
        if (resolution.outcome() == SessionResolutionOutcome.CLOSE_INVENTORY) {
            player.closeInventory();
            return;
        }
        if (resolution.outcome() == SessionResolutionOutcome.REFRESHED) {
            return;
        }

        VoteSession session = resolution.session();
        if (session == null) {
            player.closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();

        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        try {
            if (session.isInSelectionScreen()) {
                handleSelectionClick(player, session, rawSlot);
                return;
            }

            if (session.isInConfirmationScreen()) {
                handleConfirmationClick(player, session, rawSlot);
            }
        } catch (IllegalStateException | IllegalArgumentException ex) {
            voteRenderer.refresh(player, session);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!voteRenderer.isManagedInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
    }

    private SessionResolution resolveSession(Player player, InventoryClickEvent event) {
        ModNVoteInventoryHolder holder = voteRenderer.requireManagedHolder(event.getView().getTopInventory());

        Optional<VoteSession> optionalSession =
                voteSessionManager.findSession(player.getUniqueId(), holder.pollId());

        if (optionalSession.isEmpty()) {
            return SessionResolution.closeInventory();
        }

        VoteSession session = optionalSession.get();

        if (!session.playerUuid().equals(holder.playerUuid())) {
            return SessionResolution.closeInventory();
        }

        if (!voteRenderer.holderMatchesSessionScreen(holder, session)) {
            voteRenderer.refresh(player, session);
            return SessionResolution.refreshed();
        }

        return SessionResolution.continueWith(session);
    }

    private void handleSelectionClick(Player player, VoteSession session, int rawSlot) {
        Optional<Long> optionalOptionId = voteRenderer.selectionOptionIdAtSlot(session, rawSlot);
        if (optionalOptionId.isPresent()) {
            long optionId = optionalOptionId.get();
            boolean previouslySelected = session.isSelected(optionId);
            boolean changed = session.toggleRankedSelection(optionId);

            if (changed) {
                if (previouslySelected) {
                    voteSoundService.playSelectionRemoved(player);
                } else {
                    voteSoundService.playSelectionAssigned(player);
                }
                voteRenderer.refresh(player, session);
            }
            return;
        }

        if (voteRenderer.isResetSlot(rawSlot)) {
            boolean hadSelections = session.hasSelections();
            session.clearSelections();

            if (hadSelections) {
                voteSoundService.playReset(player);
                voteRenderer.refresh(player, session);
            }
            return;
        }

        if (voteRenderer.isCastSlot(rawSlot) && session.isValidSelection()) {
            session.moveToConfirmation();
            voteSoundService.playReviewAdvance(player);
            voteRenderer.refresh(player, session);
        }
    }

    private void handleConfirmationClick(Player player, VoteSession session, int rawSlot) {
        if (voteRenderer.isConfirmationBackSlot(rawSlot)) {
            session.returnToSelection();
            voteSoundService.playReturnToSelection(player);
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

            voteSoundService.playSubmitSuccess(player);

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
            voteSoundService.playSubmitFailure(player);
            player.sendMessage(messages.format(
                    "errors.vote_failed",
                    Map.of("reason", e.getMessage())
            ));
            voteRenderer.refresh(player, session);
        }
    }

    private record SessionResolution(
            SessionResolutionOutcome outcome,
            VoteSession session
    ) {
        private static SessionResolution continueWith(VoteSession session) {
            return new SessionResolution(SessionResolutionOutcome.CONTINUE, session);
        }

        private static SessionResolution closeInventory() {
            return new SessionResolution(SessionResolutionOutcome.CLOSE_INVENTORY, null);
        }

        private static SessionResolution refreshed() {
            return new SessionResolution(SessionResolutionOutcome.REFRESHED, null);
        }
    }

    private enum SessionResolutionOutcome {
        CONTINUE,
        CLOSE_INVENTORY,
        REFRESHED
    }
}