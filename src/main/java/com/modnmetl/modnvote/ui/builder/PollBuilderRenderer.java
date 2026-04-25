package com.modnmetl.modnvote.ui.builder;

import org.bukkit.entity.Player;

/**
 * Responsible for rendering the Poll Builder GUI.
 *
 * This is a placeholder scaffold – no inventory logic yet.
 */
public class PollBuilderRenderer {

    public void open(Player player, PollBuilderSession session) {
        // TODO: implement inventory UI rendering
        player.sendMessage("§7[PollBuilder] Opening builder UI for poll #" + session.getPollId());
    }
}
