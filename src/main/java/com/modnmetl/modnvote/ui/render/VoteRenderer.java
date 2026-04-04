package com.modnmetl.modnvote.ui.render;

import com.modnmetl.modnvote.ui.session.VoteSession;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Abstraction for presenting a vote session to a player.
 *
 * This keeps the session model independent from any specific UI technology.
 * Java inventory rendering is the first implementation target.
 */
public interface VoteRenderer {

    void openSelection(Player player, VoteSession session);

    void openConfirmation(Player player, VoteSession session);

    void refresh(Player player, VoteSession session);

    static void requirePlayerAndSession(Player player, VoteSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
    }
}