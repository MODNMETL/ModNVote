package com.modnmetl.modnvote.ui.builder;

import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.storage.dao.PollOptionDao;
import com.modnmetl.modnvote.platform.ModNScheduler;
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

        // Title
        if (slot == 10) {
            player.closeInventory();

            inputManager.prompt(player, input -> {
                scheduler.runAsync(() -> {
                    try {
                        pollService.updatePollTitle(
                                session.getPollId(),
                                input,
                                player.getName()
                        );
                        reopen(player, session);
                    } catch (Exception e) {
                        scheduler.runForPlayer(player,
                                () -> player.sendMessage("§cFailed to update title: " + e.getMessage()));
                    }
                });
            });
            return;
        }

        // Description
        if (slot == 12) {
            player.closeInventory();

            inputManager.prompt(player, input -> {
                scheduler.runAsync(() -> {
                    try {
                        pollService.updatePollDescription(
                                session.getPollId(),
                                input,
                                player.getName()
                        );
                        reopen(player, session);
                    } catch (Exception e) {
                        scheduler.runForPlayer(player,
                                () -> player.sendMessage("§cFailed to update description"));
                    }
                });
            });
            return;
        }

        // Options
        if (slot >= 19 && slot < 19 + session.getOptionsSnapshot().size()) {
            int index = slot - 19;
            PollOption option = session.getOptionsSnapshot().get(index);

            player.closeInventory();

            if (event.isRightClick()) {
                inputManager.prompt(player, input -> {
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
                inputManager.prompt(player, input -> {
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

        // Validate
        if (slot == 49) {
            player.sendMessage("§7Validate clicked (not implemented yet)");
            return;
        }

        // Cancel
        if (slot == 53) {
            player.sendMessage("§cCancel clicked (not implemented yet)");
        }
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

            scheduler.runForPlayer(player, () -> {
                renderer.open(player, newSession);
            });

        } catch (Exception e) {
            scheduler.runForPlayer(player,
                    () -> player.sendMessage("§cFailed to refresh builder: " + e.getMessage()));
        }
    }
}
