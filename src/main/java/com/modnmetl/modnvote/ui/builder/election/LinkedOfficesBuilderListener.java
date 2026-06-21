package com.modnmetl.modnvote.ui.builder.election;

import com.modnmetl.modnvote.domain.election.CountingMethod;
import com.modnmetl.modnvote.platform.ModNScheduler;
import com.modnmetl.modnvote.service.ElectionDefinitionService;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderSession.Action;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderSession.Screen;
import com.modnmetl.modnvote.ui.builder.election.LinkedOfficesBuilderState.OfficeDraft;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.function.Consumer;

/**
 * Handles clicks in the linked-offices builder GUI.
 *
 * In-memory edits to the {@link LinkedOfficesBuilderState} happen on the main
 * thread; only persistence (Save) is dispatched asynchronously through the
 * service layer. The GUI never writes the database directly.
 */
public final class LinkedOfficesBuilderListener implements Listener {

    private static final int SIZE = 54;

    private final LinkedOfficesBuilderSessionManager sessionManager;
    private final LinkedOfficesInputPromptManager inputManager;
    private final LinkedOfficesBuilderService builderService;
    private final ModNScheduler scheduler;
    private final LinkedOfficesBuilderRenderer renderer;

    public LinkedOfficesBuilderListener(LinkedOfficesBuilderSessionManager sessionManager,
                                        LinkedOfficesInputPromptManager inputManager,
                                        LinkedOfficesBuilderService builderService,
                                        ModNScheduler scheduler,
                                        LinkedOfficesBuilderRenderer renderer) {
        this.sessionManager = sessionManager;
        this.inputManager = inputManager;
        this.builderService = builderService;
        this.scheduler = scheduler;
        this.renderer = renderer;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof LinkedOfficesBuilderHolder builderHolder)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }

        LinkedOfficesBuilderSession session = builderHolder.getSession();
        Action action = session.actionAt(slot);
        if (action == null) {
            return;
        }

        boolean right = event.isRightClick();

        switch (action.type()) {
            case "NAV_MAIN" -> navigate(player, session, Screen.MAIN);
            case "NAV_OFFICES" -> navigate(player, session, Screen.OFFICES);
            case "NAV_CANDIDATES" -> navigate(player, session, Screen.CANDIDATES);
            case "NAV_DEPENDENCIES" -> {
                session.cancelAddDependency();
                navigate(player, session, Screen.DEPENDENCIES);
            }
            case "CLOSE" -> close(player, session);
            case "VALIDATE" -> validate(player, session);
            case "SAVE" -> save(player, session);

            case "CREATE_OFFICE" -> promptCreateOffice(player, session);
            case "OFFICE_ENTRY" -> {
                if (right) {
                    session.getState().removeOffice(action.arg());
                    session.clearValidation();
                    reopen(player, session);
                } else {
                    session.setEditingOfficeKey(action.arg());
                    navigate(player, session, Screen.OFFICE_EDITOR);
                }
            }
            case "EDIT_OFFICE_KEY" -> promptEditOfficeKey(player, session);
            case "EDIT_OFFICE_NAME" -> promptEditOfficeName(player, session);
            case "CYCLE_METHOD" -> {
                OfficeDraft office = currentOffice(session);
                if (office != null) {
                    office.setMethod(nextMethod(office.method()));
                    session.clearValidation();
                }
                reopen(player, session);
            }
            case "SEAT_ADJUST" -> {
                OfficeDraft office = currentOffice(session);
                if (office != null) {
                    office.setSeats(office.seats() + (right ? -1 : 1));
                    session.clearValidation();
                }
                reopen(player, session);
            }
            case "MAXSEL_ADJUST" -> {
                OfficeDraft office = currentOffice(session);
                if (office != null) {
                    int current = office.maxSelections() == null ? 0 : office.maxSelections();
                    int next = current + (right ? -1 : 1);
                    office.setMaxSelections(next < 1 ? null : next);
                    session.clearValidation();
                }
                reopen(player, session);
            }
            case "TOGGLE_ABSTAIN" -> {
                OfficeDraft office = currentOffice(session);
                if (office != null) {
                    office.setAllowAbstain(!office.allowAbstain());
                    session.clearValidation();
                }
                reopen(player, session);
            }
            case "DELETE_OFFICE" -> {
                session.getState().removeOffice(action.arg());
                session.clearValidation();
                navigate(player, session, Screen.OFFICES);
            }

            case "CREATE_CANDIDATE" -> promptCreateCandidate(player, session);
            case "CANDIDATE_ENTRY" -> {
                if (right) {
                    session.getState().removeCandidate(action.arg());
                    session.clearValidation();
                    reopen(player, session);
                } else {
                    session.setEditingCandidateKey(action.arg());
                    navigate(player, session, Screen.CANDIDATE_EDITOR);
                }
            }
            case "EDIT_CANDIDATE_KEY" -> promptEditCandidateKey(player, session);
            case "EDIT_CANDIDATE_NAME" -> promptEditCandidateName(player, session);
            case "TOGGLE_ELIGIBLE" -> {
                session.getState().toggleEligibility(session.getEditingCandidateKey(), action.arg());
                session.clearValidation();
                reopen(player, session);
            }
            case "DELETE_CANDIDATE" -> {
                session.getState().removeCandidate(action.arg());
                session.clearValidation();
                navigate(player, session, Screen.CANDIDATES);
            }

            case "ADD_DEPENDENCY" -> {
                session.beginAddDependency();
                reopen(player, session);
            }
            case "DEP_PICK_FROM" -> {
                session.setPendingDependencyFrom(action.arg());
                reopen(player, session);
            }
            case "DEP_PICK_TARGET" -> {
                session.getState().addExcludeWinnersDependency(session.getPendingDependencyFrom(), action.arg());
                session.cancelAddDependency();
                session.clearValidation();
                reopen(player, session);
            }
            case "DEP_CANCEL_ADD" -> {
                session.cancelAddDependency();
                reopen(player, session);
            }
            case "DEPENDENCY_ENTRY" -> {
                if (right) {
                    session.getState().removeDependency(Integer.parseInt(action.arg()));
                    session.clearValidation();
                    reopen(player, session);
                }
            }
            default -> {
                // no-op for unknown actions
            }
        }
    }

    // --- navigation / lifecycle --------------------------------------------

    private void navigate(Player player, LinkedOfficesBuilderSession session, Screen screen) {
        session.setCurrentScreen(screen);
        reopen(player, session);
    }

    private void reopen(Player player, LinkedOfficesBuilderSession session) {
        renderer.open(player, session);
    }

    private void close(Player player, LinkedOfficesBuilderSession session) {
        sessionManager.removeSession(player.getUniqueId());
        inputManager.clear(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§eLinked Offices builder closed. Use §f/modnvote edit-definition "
                + session.getPollId() + " §eto resume. Unsaved edits were discarded.");
    }

    private void validate(Player player, LinkedOfficesBuilderSession session) {
        ElectionDefinitionService.ElectionDefinitionValidationResult result =
                builderService.validate(session.getState());
        session.recordValidation(result.valid(), result.issues());

        if (result.valid()) {
            player.sendMessage("§aLinked Offices definition is VALID.");
        } else {
            player.sendMessage("§cLinked Offices definition is INVALID:");
            for (String issue : result.issues()) {
                player.sendMessage(" §8- §f" + issue);
            }
        }
        navigate(player, session, Screen.MAIN);
    }

    private void save(Player player, LinkedOfficesBuilderSession session) {
        scheduler.runAsync(() -> {
            try {
                builderService.save(session.getPollId(), session.getState(), player.getName());
                scheduler.runForPlayer(player, () -> {
                    player.sendMessage("§aSaved Linked Offices definition for poll §f#" + session.getPollId() + "§a.");
                    player.sendMessage("§8Validate, mark the poll ready, then open it to let players vote.");
                    session.recordValidation(true, java.util.List.of());
                    session.setCurrentScreen(Screen.MAIN);
                    renderer.open(player, session);
                });
            } catch (Exception e) {
                scheduler.runForPlayer(player, () ->
                        player.sendMessage("§cCannot save: " + e.getMessage()));
            }
        });
    }

    // --- chat-prompt edits --------------------------------------------------

    private void promptCreateOffice(Player player, LinkedOfficesBuilderSession session) {
        promptThen(player, "new office key", input -> {
            String key = normalizeKey(input);
            session.getState().createOffice(key);
            session.setEditingOfficeKey(key);
            session.setCurrentScreen(Screen.OFFICE_EDITOR);
            session.clearValidation();
        });
    }

    private void promptEditOfficeKey(Player player, LinkedOfficesBuilderSession session) {
        promptThen(player, "new office key", input -> {
            String newKey = normalizeKey(input);
            session.getState().renameOffice(session.getEditingOfficeKey(), newKey);
            session.setEditingOfficeKey(newKey);
            session.clearValidation();
        });
    }

    private void promptEditOfficeName(Player player, LinkedOfficesBuilderSession session) {
        promptThen(player, "office display name", input -> {
            OfficeDraft office = currentOffice(session);
            if (office != null) {
                office.setDisplayName(input.trim());
                session.clearValidation();
            }
        });
    }

    private void promptCreateCandidate(Player player, LinkedOfficesBuilderSession session) {
        promptThen(player, "new candidate key", input -> {
            String key = normalizeKey(input);
            session.getState().createCandidate(key);
            session.setEditingCandidateKey(key);
            session.setCurrentScreen(Screen.CANDIDATE_EDITOR);
            session.clearValidation();
        });
    }

    private void promptEditCandidateKey(Player player, LinkedOfficesBuilderSession session) {
        promptThen(player, "new candidate key", input -> {
            String newKey = normalizeKey(input);
            session.getState().renameCandidate(session.getEditingCandidateKey(), newKey);
            session.setEditingCandidateKey(newKey);
            session.clearValidation();
        });
    }

    private void promptEditCandidateName(Player player, LinkedOfficesBuilderSession session) {
        promptThen(player, "candidate display name", input -> {
            var candidate = session.getState().candidate(session.getEditingCandidateKey());
            if (candidate != null) {
                candidate.setDisplayName(input.trim());
                session.clearValidation();
            }
        });
    }

    /**
     * Closes the inventory, prompts for chat input, and re-applies the edit on the
     * main thread before reopening the current screen.
     */
    private void promptThen(Player player, String label, Consumer<String> edit) {
        player.closeInventory();
        inputManager.prompt(player, label, input ->
                scheduler.runForPlayer(player, () -> {
                    LinkedOfficesBuilderSession current = sessionManager.getSession(player.getUniqueId());
                    if (current == null) {
                        return;
                    }
                    edit.accept(input);
                    renderer.open(player, current);
                }));
    }

    private OfficeDraft currentOffice(LinkedOfficesBuilderSession session) {
        return session.getState().office(session.getEditingOfficeKey());
    }

    private CountingMethod nextMethod(CountingMethod method) {
        if (method == null) {
            return CountingMethod.IRV;
        }
        return switch (method) {
            case IRV -> CountingMethod.APPROVAL_TOP_N;
            case APPROVAL_TOP_N -> CountingMethod.STV;
            case STV -> CountingMethod.IRV;
        };
    }

    private String normalizeKey(String input) {
        return input.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
