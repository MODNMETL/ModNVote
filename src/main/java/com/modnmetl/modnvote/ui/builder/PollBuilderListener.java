package com.modnmetl.modnvote.ui.builder;

import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.platform.ModNScheduler;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class PollBuilderListener implements Listener {

    private final PollBuilderSessionManager sessionManager;
    private final PollBuilderInputPromptManager inputManager;
    private final PollService pollService;
    private final PollOptionDao pollOptionDao;
    private final ModNScheduler scheduler;
    private final PollBuilderRenderer renderer;

    public PollBuilderListener(
            PollBuilderSessionManager sessionManager,
            PollBuilderInputPromptManager inputManager,
            PollService pollService,
            PollOptionDao pollOptionDao,
            ModNScheduler scheduler,
            PollBuilderRenderer renderer
    ) {
        this.sessionManager = sessionManager;
        this.inputManager = inputManager;
        this.pollService = pollService;
        this.pollOptionDao = pollOptionDao;
        this.scheduler = scheduler;
        this.renderer = renderer;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof PollBuilderInventoryHolder builderHolder)) return;

        event.setCancelled(true);

        PollBuilderSession session = builderHolder.getSession();
        int slot = event.getSlot();

        if (slot == PollBuilderRenderer.TITLE_SLOT) {
            player.closeInventory();

            inputManager.prompt(player, "poll title", input -> {
                scheduler.runAsync(() -> {
                    try {
                        pollService.updatePollTitle(session.getPollId(), input, player.getName());
                        reopen(player, session);
                    } catch (Exception e) {
                        scheduler.runForPlayer(player,
                                () -> player.sendMessage("§cFailed to update title: " + e.getMessage()));
                    }
                });
            });
            return;
        }

        if (slot == PollBuilderRenderer.DESCRIPTION_SLOT) {
            player.closeInventory();

            inputManager.prompt(player, "poll description", input -> {
                scheduler.runAsync(() -> {
                    try {
                        pollService.updatePollDescription(session.getPollId(), input, player.getName());
                        reopen(player, session);
                    } catch (Exception e) {
                        scheduler.runForPlayer(player,
                                () -> player.sendMessage("§cFailed to update description"));
                    }
                });
            });
            return;
        }

        if (slot == PollBuilderRenderer.ALLOW_PARTIAL_SLOT) {
            scheduler.runAsync(() -> {
                try {
                    Poll poll = session.getPollSnapshot();
                    pollService.updatePollAllowPartialRanking(
                            session.getPollId(),
                            !poll.allowPartialRanking(),
                            player.getName()
                    );
                    reopen(player, session);
                } catch (Exception e) {
                    scheduler.runForPlayer(player,
                            () -> player.sendMessage("§cFailed to update allow-partial setting: " + e.getMessage()));
                }
            });
            return;
        }

        if (slot == PollBuilderRenderer.MAX_RANKINGS_SLOT) {
            scheduler.runAsync(() -> {
                try {
                    Poll poll = session.getPollSnapshot();
                    int optionCount = session.getOptionsSnapshot().size();
                    int nextMaxRankings = nextMaxRankings(poll.maxRankings(), optionCount);

                    pollService.updatePollMaxRankings(
                            session.getPollId(),
                            nextMaxRankings,
                            player.getName()
                    );
                    reopen(player, session);
                } catch (Exception e) {
                    scheduler.runForPlayer(player,
                            () -> player.sendMessage("§cFailed to update max rankings: " + e.getMessage()));
                }
            });
            return;
        }

        if (slot >= PollBuilderRenderer.FIRST_OPTION_SLOT
                && slot < PollBuilderRenderer.FIRST_OPTION_SLOT + session.getOptionsSnapshot().size()) {
            int index = slot - PollBuilderRenderer.FIRST_OPTION_SLOT;
            PollOption option = session.getOptionsSnapshot().get(index);

            player.closeInventory();

            if (event.isRightClick()) {
                inputManager.prompt(player, "option " + (index + 1) + " description", input -> {
                    scheduler.runAsync(() -> {
                        try {
                            pollService.updateOptionDescription(
                                    session.getPollId(),
                                    option.optionId(),
                                    input,
                                    player.getName()
                            );
                            reopen(player, session);
                        } catch (Exception e) {
                            scheduler.runForPlayer(player,
                                    () -> player.sendMessage("§cFailed to update option description"));
                        }
                    });
                });
            } else {
                inputManager.prompt(player, "option " + (index + 1) + " name", input -> {
                    scheduler.runAsync(() -> {
                        try {
                            pollService.updateOptionName(
                                    session.getPollId(),
                                    option.optionId(),
                                    input,
                                    player.getName()
                            );
                            reopen(player, session);
                        } catch (Exception e) {
                            scheduler.runForPlayer(player,
                                    () -> player.sendMessage("§cFailed to update option name"));
                        }
                    });
                });
            }
            return;
        }

        if (slot == PollBuilderRenderer.READY_SLOT) {
            scheduler.runAsync(() -> {
                try {
                    pollService.readyPoll(session.getPollId(), player.getName());
                    sessionManager.removeSession(player.getUniqueId());
                    scheduler.runForPlayer(player, () -> {
                        player.closeInventory();
                        player.sendMessage("§aPoll marked READY.");
                    });
                } catch (Exception e) {
                    scheduler.runForPlayer(player,
                            () -> player.sendMessage("§cCannot mark ready: " + e.getMessage()));
                }
            });
            return;
        }

        if (slot == PollBuilderRenderer.CANCEL_SLOT) {
            player.sendMessage("§cCancel clicked (not implemented yet)");
        }
    }

    private int nextMaxRankings(int currentMaxRankings, int optionCount) {
        if (optionCount <= 1) {
            return 0;
        }
        if (currentMaxRankings <= 0 || currentMaxRankings >= optionCount) {
            return 1;
        }
        if (currentMaxRankings + 1 >= optionCount) {
            return 0;
        }
        return currentMaxRankings + 1;
    }

    private void reopen(Player player, PollBuilderSession oldSession) {
        try {
            var poll = pollService.findPollById(oldSession.getPollId());
            var options = pollOptionDao.findOptionsByPollId(oldSession.getPollId());

            PollBuilderSession newSession = new PollBuilderSession(
                    player.getUniqueId(),
                    oldSession.getPollId(),
                    poll,
                    options
            );

            sessionManager.createOrReplaceSession(newSession);

            scheduler.runForPlayer(player, () -> renderer.open(player, newSession));

        } catch (Exception e) {
            scheduler.runForPlayer(player,
                    () -> player.sendMessage("§cFailed to refresh builder: " + e.getMessage()));
        }
    }
}
