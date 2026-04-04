package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.ui.format.BallotSummaryFormatter;
import com.modnmetl.modnvote.ui.session.VoteSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

/**
 * Initial Java inventory renderer skeleton for ranked voting sessions.
 *
 * This class currently establishes the rendering contract and inventory-opening
 * flow, while keeping layout, title handling, and slot details intentionally light until slot mapping and
 * click-handling are added in the next phase.
 */
public final class JavaInventoryVoteRenderer implements VoteRenderer {

    private static final int SELECTION_SIZE = 54;
    private static final int CONFIRMATION_SIZE = 27;
    private static final String SELECTION_TITLE_PREFIX = "Vote: ";
    private static final String CONFIRMATION_TITLE_PREFIX = "Confirm Vote: ";

    private final BallotSummaryFormatter ballotSummaryFormatter;

    public JavaInventoryVoteRenderer(BallotSummaryFormatter ballotSummaryFormatter) {
        this.ballotSummaryFormatter = Objects.requireNonNull(ballotSummaryFormatter, "ballotSummaryFormatter");
    }

    @Override
    public void openSelection(Player player, VoteSession session) {
        VoteRenderer.requirePlayerAndSession(player, session);

        Inventory inventory = Bukkit.createInventory(
                player,
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
                player,
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
        // Intentional skeleton only for this phase.
        // The next phase will add:
        // - option placement
        // - summary item
        // - cast button state
        // - reset/cancel controls
        ballotSummaryFormatter.formatSelectionSummary(session);
        ballotSummaryFormatter.formatCastButtonSummary(session);
    }

    private void populateConfirmationInventory(Inventory inventory, VoteSession session) {
        // Intentional skeleton only for this phase.
        // The next phase will add:
        // - centered summary item
        // - confirm button
        // - back/cancel button
        ballotSummaryFormatter.formatConfirmationSummary(session);
    }
}