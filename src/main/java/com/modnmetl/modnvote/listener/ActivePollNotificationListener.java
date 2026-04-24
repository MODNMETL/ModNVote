package com.modnmetl.modnvote.listener;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Notifies players of open polls they have not yet participated in.
 *
 * This is intentionally:
 * - non-authoritative
 * - identity-aware only (no ballot data access)
 */
public final class ActivePollNotificationListener implements Listener {

    private final Plugin plugin;
    private final PollService pollService;
    private final BallotService ballotService;
    private final Logger logger;

    public ActivePollNotificationListener(Plugin plugin,
                                          PollService pollService,
                                          BallotService ballotService,
                                          Logger logger) {
        this.plugin = plugin;
        this.pollService = pollService;
        this.ballotService = ballotService;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> notifyPlayer(player), 40L);
    }

    private void notifyPlayer(Player player) {
        try {
            List<Poll> polls = pollService.listPolls();
            List<Poll> openUnvoted = new ArrayList<>();

            for (Poll poll : polls) {
                if (!"OPEN".equals(poll.status().name())) {
                    continue;
                }

                boolean included = ballotService.verifyVoterInclusion(
                        poll.pollId(),
                        player.getUniqueId().toString()
                ).included();

                if (!included) {
                    openUnvoted.add(poll);
                }
            }

            if (openUnvoted.isEmpty()) {
                return;
            }

            player.sendMessage("§6[ModNVote] §aYou have open polls awaiting your vote:");

            int limit = Math.min(openUnvoted.size(), 3);
            for (int i = 0; i < limit; i++) {
                Poll poll = openUnvoted.get(i);
                player.sendMessage(" §8- §f#" + poll.pollId() + " §b" + poll.title());
            }

            if (openUnvoted.size() > limit) {
                player.sendMessage(" §7...and " + (openUnvoted.size() - limit) + " more.");
            }

            player.sendMessage("§7Use §e/modnvote vote <pollId> §7to participate.");

        } catch (PollServiceException e) {
            logger.warning("Failed to compute join poll notifications: " + e.getMessage());
        } catch (Exception e) {
            logger.warning("Unexpected error during join poll notification: " + e.getMessage());
        }
    }
}
