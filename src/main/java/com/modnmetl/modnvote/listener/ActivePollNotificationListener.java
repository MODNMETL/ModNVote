package com.modnmetl.modnvote.listener;

import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.platform.ModNScheduler;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class ActivePollNotificationListener implements Listener {

    private final ModNScheduler scheduler;
    private final PollService pollService;
    private final BallotService ballotService;
    private final Logger logger;

    public ActivePollNotificationListener(ModNScheduler scheduler,
                                          PollService pollService,
                                          BallotService ballotService,
                                          Logger logger) {
        this.scheduler = scheduler;
        this.pollService = pollService;
        this.ballotService = ballotService;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        scheduler.runForPlayerLater(player, () -> {
            scheduler.runAsync(() -> process(playerId));
        }, 40L);
    }

    private void process(UUID playerId) {
        try {
            List<Poll> polls = pollService.listPolls();
            List<Poll> openUnvoted = new ArrayList<>();

            for (Poll poll : polls) {
                if (!"OPEN".equals(poll.status().name())) {
                    continue;
                }

                boolean included = ballotService.verifyVoterInclusion(
                        poll.pollId(),
                        playerId.toString()
                ).included();

                if (!included) {
                    openUnvoted.add(poll);
                }
            }

            if (openUnvoted.isEmpty()) {
                return;
            }

            scheduler.runForPlayerLater(
                    org.bukkit.Bukkit.getPlayer(playerId),
                    () -> sendMessage(org.bukkit.Bukkit.getPlayer(playerId), openUnvoted),
                    1L
            );

        } catch (PollServiceException e) {
            logger.warning("Failed to compute join poll notifications: " + e.getMessage());
        } catch (Exception e) {
            logger.warning("Unexpected error during join poll notification: " + e.getMessage());
        }
    }

    private void sendMessage(Player player, List<Poll> openUnvoted) {
        if (player == null || !player.isOnline()) {
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
    }
}
