package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.ui.feedback.VoteSoundService;
import com.modnmetl.modnvote.ui.session.election.LinkedOfficesVoteSession;
import com.modnmetl.modnvote.ui.session.election.LinkedOfficesVoteSessionManager;
import com.modnmetl.modnvote.ui.submit.VoteSubmissionCoordinator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles interaction safety and screen transitions for linked-offices vote GUIs.
 *
 * <p>Mirrors {@code YesNoVoteGuiListener}: it cancels all interaction with managed
 * inventories, resolves the player's session, and applies the click to the
 * Bukkit-free {@link LinkedOfficesVoteSession} state. Ballot building, validation,
 * lifecycle gating, and storage happen in the service layer via the submission
 * coordinator; this class only navigates and toggles selections.
 */
public final class LinkedOfficesVoteListener implements Listener {

    private final LinkedOfficesVoteSessionManager sessionManager;
    private final LinkedOfficesVoteRenderer renderer;
    private final VoteSubmissionCoordinator voteSubmissionCoordinator;
    private final VoteSoundService voteSoundService;
    private final MessageService messages;

    public LinkedOfficesVoteListener(LinkedOfficesVoteSessionManager sessionManager,
                                     LinkedOfficesVoteRenderer renderer,
                                     VoteSubmissionCoordinator voteSubmissionCoordinator,
                                     VoteSoundService voteSoundService,
                                     MessageService messages) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.voteSubmissionCoordinator = Objects.requireNonNull(voteSubmissionCoordinator, "voteSubmissionCoordinator");
        this.voteSoundService = Objects.requireNonNull(voteSoundService, "voteSoundService");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!renderer.isManagedInventory(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        clearCursor(player);

        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        LinkedOfficesInventoryHolder holder = renderer.requireManagedHolder(event.getView().getTopInventory());
        Optional<LinkedOfficesVoteSession> optionalSession =
                sessionManager.findSession(player.getUniqueId(), holder.pollId());
        if (optionalSession.isEmpty()) {
            player.closeInventory();
            return;
        }

        LinkedOfficesVoteSession session = optionalSession.get();
        if (!session.playerUuid().equals(holder.playerUuid())) {
            player.closeInventory();
            return;
        }
        if (!renderer.holderMatchesSessionScreen(holder, session)) {
            renderer.refresh(player, session);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        try {
            if (session.isInOverviewScreen()) {
                handleOverviewClick(player, session, rawSlot);
            } else if (session.isInOfficeScreen()) {
                handleOfficeClick(player, session, rawSlot);
            } else if (session.isInReviewScreen()) {
                handleReviewClick(player, session, rawSlot);
            }
        } catch (IllegalStateException | IllegalArgumentException ex) {
            renderer.refresh(player, session);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!renderer.isManagedInventory(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            clearCursor(player);
        }
    }

    private void handleOverviewClick(Player player, LinkedOfficesVoteSession session, int rawSlot) {
        Optional<String> office = renderer.officeKeyAtSlot(session, rawSlot);
        if (office.isPresent()) {
            session.openOffice(office.get());
            voteSoundService.playReviewAdvance(player);
            renderer.refresh(player, session);
            return;
        }
        if (renderer.isActionSlot(rawSlot) && session.state().isSubmittable()) {
            session.moveToReview();
            voteSoundService.playReviewAdvance(player);
            renderer.refresh(player, session);
        }
    }

    private void handleOfficeClick(Player player, LinkedOfficesVoteSession session, int rawSlot) {
        Optional<String> candidate = renderer.candidateKeyAtSlot(session, rawSlot);
        if (candidate.isPresent()) {
            boolean wasSelected = session.state().isSelected(session.currentOfficeKey(), candidate.get());
            boolean changed = session.state().toggle(session.currentOfficeKey(), candidate.get());
            if (changed) {
                if (wasSelected) {
                    voteSoundService.playSelectionRemoved(player);
                } else {
                    voteSoundService.playSelectionAssigned(player);
                }
                session.touch();
                renderer.refresh(player, session);
            }
            return;
        }
        if (renderer.isClearSlot(rawSlot)) {
            if (session.state().clearOffice(session.currentOfficeKey())) {
                voteSoundService.playReset(player);
                session.touch();
                renderer.refresh(player, session);
            }
            return;
        }
        if (renderer.isBackSlot(rawSlot)) {
            session.returnToOverview();
            voteSoundService.playReturnToSelection(player);
            renderer.refresh(player, session);
        }
    }

    private void handleReviewClick(Player player, LinkedOfficesVoteSession session, int rawSlot) {
        if (renderer.isBackSlot(rawSlot)) {
            session.returnToOverview();
            voteSoundService.playReturnToSelection(player);
            renderer.refresh(player, session);
            return;
        }
        if (renderer.isActionSlot(rawSlot) && session.state().isSubmittable()) {
            submitBallot(player, session);
        }
    }

    private void submitBallot(Player player, LinkedOfficesVoteSession session) {
        try {
            VoteSubmissionCoordinator.LinkedOfficesSubmissionOutcome outcome =
                    voteSubmissionCoordinator.submitLinkedOfficesVote(player, session);

            sessionManager.removeSession(player.getUniqueId(), session.pollId());
            player.closeInventory();

            voteSoundService.playSubmitSuccess(player);

            player.sendMessage(messages.get("vote.submit_success"));
            player.sendMessage(messages.format("vote.poll_context", Map.of(
                    "poll_id", String.valueOf(session.pollId()),
                    "title", session.poll().title()
            )));
            player.sendMessage(messages.get("vote.education_privacy"));
            player.sendMessage(messages.get("vote.education_verification"));
            player.sendMessage(messages.formatRaw("vote.ballot_hash",
                    Map.of("ballot_hash", outcome.submissionResult().ballotHash())));
            player.sendMessage(messages.formatRaw("vote.participation_receipt",
                    Map.of("participation_receipt", outcome.submissionResult().participationReceipt())));
            player.sendMessage(messages.formatRaw("vote.ballot_proof_phrase",
                    Map.of("ballot_proof_phrase", outcome.submissionResult().proofPhrase())));
            player.sendMessage(messages.get("vote.ballot_proof_warning"));
        } catch (PollServiceException e) {
            sessionManager.removeSession(player.getUniqueId(), session.pollId());
            player.closeInventory();

            voteSoundService.playSubmitFailure(player);
            player.sendMessage(messages.format("errors.vote_failed", Map.of("reason", e.getMessage())));
        }
    }

    private void clearCursor(Player player) {
        player.setItemOnCursor(new ItemStack(Material.AIR));
    }
}
