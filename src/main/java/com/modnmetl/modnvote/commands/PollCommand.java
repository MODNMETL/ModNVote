package com.modnmetl.modnvote.commands;

import com.modnmetl.modnvote.ModNVotePlugin;
import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.presentation.ResultDisplayFormatter;
import com.modnmetl.modnvote.publication.WitnessPublicationService;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.IntegrityVerificationService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.service.ResultService;
import com.modnmetl.modnvote.storage.PollOptionDao;
import com.modnmetl.modnvote.ui.builder.PollBuilderSession;
import com.modnmetl.modnvote.ui.render.JavaInventoryVoteRenderer;
import com.modnmetl.modnvote.ui.render.YesNoInventoryVoteRenderer;
import com.modnmetl.modnvote.ui.session.VoteSessionManager;
import com.modnmetl.modnvote.ui.session.YesNoVoteSessionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Temporary 2.0 root command scaffold.
 *
 * This command layer is intentionally thin:
 * - user/admin-facing messaging is sourced from messages.yml
 * - business rules remain in the service layer
 * - integrity reporting combines participation and ballot integrity checks
 * - vote GUI opening is delegated to poll-type-specific session managers and renderers
 */
public final class PollCommand implements CommandExecutor, TabCompleter {

    private final ModNVotePlugin plugin;
    private final PollService pollService;
    private final BallotService ballotService;
    private final IntegrityVerificationService integrityVerificationService;
    private final ResultService resultService;
    private final WitnessPublicationService witnessPublicationService;
    private final MessageService messages;
    private final VoteSessionManager voteSessionManager;
    private final YesNoVoteSessionManager yesNoVoteSessionManager;
    private final JavaInventoryVoteRenderer rankedVoteRenderer;
    private final YesNoInventoryVoteRenderer yesNoVoteRenderer;
    private final PollOptionDao pollOptionDao;

    public PollCommand(ModNVotePlugin plugin,
                       PollService pollService,
                       BallotService ballotService,
                       IntegrityVerificationService integrityVerificationService,
                       ResultService resultService,
                       WitnessPublicationService witnessPublicationService,
                       MessageService messages,
                       VoteSessionManager voteSessionManager,
                       YesNoVoteSessionManager yesNoVoteSessionManager,
                       JavaInventoryVoteRenderer rankedVoteRenderer,
                       YesNoInventoryVoteRenderer yesNoVoteRenderer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pollService = Objects.requireNonNull(pollService, "pollService");
        this.ballotService = Objects.requireNonNull(ballotService, "ballotService");
        this.integrityVerificationService = Objects.requireNonNull(integrityVerificationService, "integrityVerificationService");
        this.resultService = Objects.requireNonNull(resultService, "resultService");
        this.witnessPublicationService = Objects.requireNonNull(witnessPublicationService, "witnessPublicationService");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.voteSessionManager = Objects.requireNonNull(voteSessionManager, "voteSessionManager");
        this.yesNoVoteSessionManager = Objects.requireNonNull(yesNoVoteSessionManager, "yesNoVoteSessionManager");
        this.rankedVoteRenderer = Objects.requireNonNull(rankedVoteRenderer, "rankedVoteRenderer");
        this.yesNoVoteRenderer = Objects.requireNonNull(yesNoVoteRenderer, "yesNoVoteRenderer");
        this.pollOptionDao = new PollOptionDao(plugin.getDatabaseManager());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);

        switch (subcommand) {
            case "status" -> {
                sender.sendMessage(messages.getRaw("status.header"));
                sender.sendMessage(messages.formatRaw("status.poll_service",
                        Map.of("value", pollService.getStatusSummary())));
                sender.sendMessage(messages.formatRaw("status.ballot_service",
                        Map.of("value", ballotService.getStatusSummary())));
                sender.sendMessage(messages.formatRaw("status.integrity_service",
                        Map.of("value", integrityVerificationService.getStatusSummary())));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("modnvote.admin.reload")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                plugin.reloadPluginConfiguration();
                sender.sendMessage(messages.get("reload.success"));
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("modnvote.admin.poll.list")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                try {
                    List<Poll> polls = pollService.listPolls();
                    if (polls.isEmpty()) {
                        sender.sendMessage(messages.get("poll.list_empty"));
                        return true;
                    }

                    sender.sendMessage(messages.getRaw("poll.list_header"));
                    for (Poll poll : polls) {
                        sender.sendMessage(messages.formatRaw("poll.list_entry", Map.of(
                                "poll_id", String.valueOf(poll.pollId()),
                                "status", poll.status().name(),
                                "title", poll.title(),
                                "type", poll.pollType().name()
                        )));
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.list_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "create" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " create <yes_no|ranked_single_winner> [optionCount]");
                    return true;
                }

                try {
                    PollType pollType = parsePollType(args[1]);

                    if (pollType == PollType.RANKED_SINGLE_WINNER) {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(messages.get("general.players_only"));
                            return true;
                        }

                        if (args.length < 3) {
                            sender.sendMessage("§cUsage: /" + label + " create ranked_single_winner <optionCount>");
                            return true;
                        }

                        int optionCount = parseOptionCount(args[2]);

                        long pollId = pollService.createPoll(sender.getName(), pollType);

                        for (int i = 1; i <= optionCount; i++) {
                            pollService.addOption(
                                    pollId,
                                    "option_" + i,
                                    "Option " + i,
                                    "Placeholder description for option " + i + ".",
                                    sender.getName()
                            );
                        }

                        Poll poll = requirePoll(pollId);
                        List<PollOption> options = findOptions(pollId);

                        PollBuilderSession session = new PollBuilderSession(
                                player.getUniqueId(),
                                pollId,
                                poll,
                                options
                        );

                        plugin.getPollBuilderSessionManager().createOrReplaceSession(session);
                        plugin.getPollBuilderRenderer().open(player, session);

                        sender.sendMessage("§aCreated DRAFT ranked poll §f#" + pollId + "§a with §f"
                                + optionCount + "§a placeholder options.");
                        sender.sendMessage("§7Poll Builder opened. Click fields to edit them.");
                        return true;
                    }

                    if (pollType == PollType.YES_NO) {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(messages.get("general.players_only"));
                            return true;
                        }

                        long pollId = pollService.createPoll(sender.getName(), pollType);

                        Poll poll = requirePoll(pollId);
                        List<PollOption> options = findOptions(pollId);

                        PollBuilderSession session = new PollBuilderSession(
                                player.getUniqueId(),
                                pollId,
                                poll,
                                options
                        );

                        plugin.getPollBuilderSessionManager().createOrReplaceSession(session);
                        plugin.getPollBuilderRenderer().open(player, session);

                        sender.sendMessage("§aCreated DRAFT Yes/No poll §f#" + pollId + "§a.");
                        sender.sendMessage("§7Poll Builder opened. Click fields to edit them.");
                        return true;
                    }

                    long pollId = pollService.createPoll(sender.getName(), pollType);
                    Poll poll = requirePoll(pollId);

                    sender.sendMessage("§aCreated DRAFT poll §f#" + pollId + "§a (" + poll.pollType().name() + ").");
                    sender.sendMessage("§7Poll type is not yet supported by the Poll Builder.");
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.create_failed",
                            Map.of("reason", e.getMessage())));
                } catch (NumberFormatException e) {
                    sender.sendMessage(messages.format("errors.create_failed",
                            Map.of("reason", "Option count must be a whole number.")));
                }
                return true;
            }
            case "edit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("general.players_only"));
                    return true;
                }

                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " edit <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);

                    if (poll.status() != PollStatus.DRAFT) {
                        sender.sendMessage("§cOnly DRAFT polls can be edited in the Poll Builder.");
                        return true;
                    }

                    List<PollOption> options = findOptions(pollId);

                    PollBuilderSession session = new PollBuilderSession(
                            player.getUniqueId(),
                            pollId,
                            poll,
                            options
                    );

                    plugin.getPollBuilderSessionManager().createOrReplaceSession(session);
                    plugin.getPollBuilderRenderer().open(player, session);

                    sender.sendMessage("§aOpened Poll Builder for draft poll #" + pollId + ".");
                } catch (PollServiceException e) {
                    sender.sendMessage("§cUnable to open Poll Builder: " + e.getMessage());
                }

                return true;
            }
            case "clone" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " clone <sourcePollId>");
                    return true;
                }

                try {
                    long sourcePollId = parsePollId(args[1]);
                    long clonedPollId = pollService.clonePoll(sourcePollId, sender.getName());

                    Poll clonedPoll = requirePoll(clonedPollId);
                    List<PollOption> clonedOptions = findOptions(clonedPollId);

                    sender.sendMessage("§aCloned poll §f#" + sourcePollId
                            + "§a into new DRAFT poll §f#" + clonedPollId + "§a.");

                    if (sender instanceof Player player) {
                        PollBuilderSession session = new PollBuilderSession(
                                player.getUniqueId(),
                                clonedPollId,
                                clonedPoll,
                                clonedOptions
                        );

                        plugin.getPollBuilderSessionManager().createOrReplaceSession(session);
                        plugin.getPollBuilderRenderer().open(player, session);

                        sender.sendMessage("§7Poll Builder opened for the cloned draft.");
                    } else {
                        sender.sendMessage("§7Use §e/" + label + " edit " + clonedPollId
                                + " §7in-game to review and adjust the cloned draft.");
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.create_failed",
                            Map.of("reason", e.getMessage())));
                }

                return true;
            }
            case "checkpoint" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " checkpoint <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);

                    WitnessPublicationService.ManualCheckpointPublicationResult result =
                            witnessPublicationService.publishManualCheckpoint(pollId);

                    sender.sendMessage("§aCheckpoint published for poll §f#" + result.pollId()
                            + "§a (" + result.ballotCount() + " ballots, "
                            + result.webhookCount() + " webhook(s)).");

                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.create_failed",
                            Map.of("reason", e.getMessage())));
                }

                return true;
            }
            case "guide" -> {
                sendGuide(sender, label);
                return true;
            }
            case "rankedpolldemo" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                try {
                    long pollId = pollService.createRankedPollDemo(sender.getName());
                    Poll poll = pollService.findPollById(pollId);

                    String title = poll != null ? poll.title() : "Ranked Poll Demo";

                    sender.sendMessage("§aCreated ranked poll demo §f#" + pollId + "§a: §f" + title);
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.create_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "show" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.show", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    handleShow(sender, pollId);
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.show_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "validate" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.validate", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    handleValidate(sender, pollId);
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.validate_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "ready" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.ready", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);

                    pollService.readyPoll(pollId, sender.getName());

                    sender.sendMessage(messages.format("poll.ready", Map.of(
                            "poll_id", String.valueOf(pollId),
                            "title", poll.title()
                    )));
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.ready_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "set" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /" + label + " set <pollId> <title|description|maxrankings|allowpartial> <value>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    String field = args[2].toLowerCase(Locale.ROOT);

                    switch (field) {
                        case "title" -> {
                            String title = joinArgs(args, 3);
                            pollService.updatePollTitle(pollId, title, sender.getName());
                            sender.sendMessage("§aUpdated title for poll §f#" + pollId + "§a to: §f" + title);
                        }
                        case "description" -> {
                            String description = joinArgs(args, 3);
                            pollService.updatePollDescription(pollId, description, sender.getName());
                            sender.sendMessage("§aUpdated description for poll §f#" + pollId + "§a.");
                        }
                        case "maxrankings" -> {
                            int maxRankings = Integer.parseInt(args[3]);
                            pollService.updatePollMaxRankings(pollId, maxRankings, sender.getName());
                            sender.sendMessage("§aUpdated max rankings for poll §f#" + pollId + "§a to §f" + maxRankings + "§a.");
                        }
                        case "allowpartial" -> {
                            boolean allowPartial = parseBoolean(args[3], "allowpartial");
                            pollService.updatePollAllowPartialRanking(pollId, allowPartial, sender.getName());
                            sender.sendMessage("§aUpdated allow-partial for poll §f#" + pollId + "§a to §f" + allowPartial + "§a.");
                        }
                        default -> sender.sendMessage("§cUsage: /" + label + " set <pollId> <title|description|maxrankings|allowpartial> <value>");
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.edit_failed",
                            Map.of("reason", e.getMessage())));
                } catch (NumberFormatException e) {
                    sender.sendMessage(messages.format("errors.edit_failed",
                            Map.of("reason", "A numeric value was expected.")));
                }
                return true;
            }
            case "delete" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " delete <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    pollService.deletePoll(pollId, sender.getName());
                    sender.sendMessage("§aDeleted poll §f#" + pollId + "§a.");
                } catch (PollServiceException e) {
                    sender.sendMessage("§cFailed to delete poll: §f" + e.getMessage());
                }
                return true;
            }
            case "option" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.option_add", Map.of("label", label)));
                    return true;
                }

                try {
                    String action = args[1].toLowerCase(Locale.ROOT);

                    switch (action) {
                        case "add" -> {
                            if (args.length < 5) {
                                sender.sendMessage(messages.format("usage.option_add", Map.of("label", label)));
                                return true;
                            }

                            long pollId = parsePollId(args[2]);
                            String key = args[3];

                            String[] parsed = splitNameAndDescription(joinArgs(args, 4));
                            String displayName = parsed[0];
                            String description = parsed[1];

                            long optionId = pollService.addOption(pollId, key, displayName, description, sender.getName());

                            sender.sendMessage(messages.format("poll.option_added", Map.of(
                                    "poll_id", String.valueOf(pollId),
                                    "option_id", String.valueOf(optionId),
                                    "option_name", displayName
                            )));
                        }
                        case "edit" -> {
                            if (args.length < 6) {
                                sender.sendMessage(messages.format("usage.option_edit_name", Map.of("label", label)));
                                return true;
                            }

                            long pollId = parsePollId(args[2]);
                            long optionId = Long.parseLong(args[3]);
                            String field = args[4].toLowerCase(Locale.ROOT);

                            if ("name".equals(field)) {
                                String displayName = joinArgs(args, 5);
                                pollService.updateOptionName(pollId, optionId, displayName, sender.getName());
                                sender.sendMessage(messages.format("poll.option_updated_name", Map.of(
                                        "poll_id", String.valueOf(pollId),
                                        "option_id", String.valueOf(optionId),
                                        "option_name", displayName
                                )));
                                return true;
                            }

                            if ("description".equals(field)) {
                                String description = joinArgs(args, 5);
                                pollService.updateOptionDescription(pollId, optionId, description, sender.getName());
                                sender.sendMessage(messages.format("poll.option_updated_description", Map.of(
                                        "poll_id", String.valueOf(pollId),
                                        "option_id", String.valueOf(optionId)
                                )));
                                return true;
                            }

                            sender.sendMessage(messages.format("usage.option_edit_name", Map.of("label", label)));
                        }
                        case "move" -> {
                            if (args.length < 5) {
                                sender.sendMessage(messages.format("usage.option_move", Map.of("label", label)));
                                return true;
                            }

                            long pollId = parsePollId(args[2]);
                            long optionId = Long.parseLong(args[3]);
                            int displayOrder = Integer.parseInt(args[4]);

                            pollService.moveOption(pollId, optionId, displayOrder, sender.getName());
                            sender.sendMessage(messages.format("poll.option_moved", Map.of(
                                    "poll_id", String.valueOf(pollId),
                                    "option_id", String.valueOf(optionId),
                                    "display_order", String.valueOf(displayOrder)
                            )));
                        }
                        case "remove" -> {
                            if (args.length < 4) {
                                sender.sendMessage(messages.format("usage.option_remove", Map.of("label", label)));
                                return true;
                            }

                            long pollId = parsePollId(args[2]);
                            long optionId = Long.parseLong(args[3]);

                            pollService.removeOption(pollId, optionId, sender.getName());
                            sender.sendMessage(messages.format("poll.option_removed", Map.of(
                                    "poll_id", String.valueOf(pollId),
                                    "option_id", String.valueOf(optionId)
                            )));
                        }
                        default -> sender.sendMessage(messages.format("usage.option_add", Map.of("label", label)));
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.option_failed",
                            Map.of("reason", e.getMessage())));
                } catch (NumberFormatException e) {
                    sender.sendMessage(messages.format("errors.option_failed",
                            Map.of("reason", "A numeric value was expected.")));
                }
                return true;
            }
            case "open" -> {
                if (!sender.hasPermission("modnvote.admin.poll.open")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.open", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);
                    pollService.openPoll(pollId, sender.getName());

                    Poll openedPoll = requirePoll(pollId);
                    witnessPublicationService.publishPollOpened(openedPoll, findOptions(pollId));

                    sender.sendMessage(messages.format("poll.opened", Map.of(
                            "poll_id", String.valueOf(pollId),
                            "title", poll.title()
                    )));
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.open_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "close" -> {
                if (!sender.hasPermission("modnvote.admin.poll.close")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.close", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);

                    pollService.closePoll(pollId, sender.getName());

                    Poll closedPoll = requirePoll(pollId);
                    ResultService.PollResult result = resultService.getPollResult(pollId);
                    witnessPublicationService.publishPollClosed(closedPoll, findOptions(pollId), result);

                    sender.sendMessage(messages.format("poll.closed", Map.of(
                            "poll_id", String.valueOf(pollId),
                            "title", poll.title()
                    )));
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.close_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "result" -> {
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.result", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    handleResultDisplay(sender, pollId);
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.result_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "publishresult" -> {
                if (!sender.hasPermission("modnvote.admin.poll.close")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /" + label + " publishresult <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);

                    if (poll.status() != PollStatus.CLOSED) {
                        sender.sendMessage("§cOnly CLOSED polls can have results published.");
                        return true;
                    }

                    ResultService.PollResult result = resultService.getPollResult(pollId);
                    witnessPublicationService.publishPollClosed(poll, findOptions(pollId), result);

                    sender.sendMessage("§aPublished closed result for poll §f#" + pollId
                            + "§a to configured witness webhook(s).");
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.result_failed",
                            Map.of("reason", e.getMessage())));
                }

                return true;
            }
            case "mypolls" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("general.players_only"));
                    return true;
                }
                if (!sender.hasPermission("modnvote.verify")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                try {
                    List<BallotService.ParticipationSummary> summaries =
                            new ArrayList<>(ballotService.listParticipatedPolls(player.getUniqueId().toString()));

                    if (summaries.isEmpty()) {
                        sender.sendMessage(messages.get("mypolls.empty"));
                        return true;
                    }

                    summaries.sort((a, b) -> Long.compare(b.pollId(), a.pollId()));

                    sender.sendMessage(messages.getRaw("mypolls.header"));
                    for (BallotService.ParticipationSummary summary : summaries) {
                        String statusColour = switch (summary.pollStatus()) {
                            case "DRAFT" -> "§7";
                            case "READY" -> "§e";
                            case "OPEN" -> "§a";
                            case "CLOSED" -> "§c";
                            case "ARCHIVED" -> "§8";
                            default -> "§f";
                        };

                        sender.sendMessage("§6#" + summary.pollId()
                                + " §f- §b" + summary.pollTitle()
                                + " §7[" + statusColour + summary.pollStatus() + "§7]");
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.verify_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "verify" -> {
                if (!sender.hasPermission("modnvote.verify")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("general.players_only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.verify_participation", Map.of("label", label)));
                    return true;
                }

                try {
                    if (args.length == 2) {
                        long pollId = parsePollId(args[1]);
                        handleParticipationVerification(sender, player, pollId);
                        return true;
                    }

                    String verifyType = args[1].toLowerCase(Locale.ROOT);

                    if ("participation".equals(verifyType)) {
                        if (args.length < 3) {
                            sender.sendMessage(messages.format("usage.verify_participation", Map.of("label", label)));
                            return true;
                        }

                        long pollId = parsePollId(args[2]);
                        handleParticipationVerification(sender, player, pollId);
                        return true;
                    }

                    if ("ballot".equals(verifyType)) {
                        if (args.length < 4) {
                            sender.sendMessage(messages.format("usage.verify_ballot", Map.of("label", label)));
                            return true;
                        }

                        long pollId = parsePollId(args[2]);
                        String ballotProofPhrase = normalizeBallotProofPhrase(joinArgs(args, 3));
                        handleBallotVerification(sender, pollId, ballotProofPhrase);
                        return true;
                    }

                    sender.sendMessage(messages.format("usage.verify_participation", Map.of("label", label)));
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.verify_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "vote" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("general.players_only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.format("usage.vote", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);

                    if (poll.status() != PollStatus.OPEN) {
                        throw new PollServiceException(messages.getRaw("errors.vote_not_open"));
                    }

                    List<PollOption> options = pollOptionDao.findOptionsByPollId(pollId);
                    if (options.isEmpty()) {
                        throw new PollServiceException("Poll #" + pollId + " has no selectable options.");
                    }

                    voteSessionManager.removeSession(player.getUniqueId());
                    yesNoVoteSessionManager.removeSession(player.getUniqueId());

                    player.sendMessage(messages.format("vote.gui_opening", Map.of(
                            "title", poll.title()
                    )));

                    if (poll.pollType() == PollType.RANKED_SINGLE_WINNER) {
                        voteSessionManager.createOrReplaceSession(player.getUniqueId(), poll, options);
                        rankedVoteRenderer.openSelection(player, voteSessionManager.getRequiredSession(player.getUniqueId()));
                        return true;
                    }

                    if (poll.pollType() == PollType.YES_NO) {
                        yesNoVoteSessionManager.createOrReplaceSession(player.getUniqueId(), poll, options);
                        yesNoVoteRenderer.openSelection(player, yesNoVoteSessionManager.getRequiredSession(player.getUniqueId()));
                        return true;
                    }

                    throw new PollServiceException("Poll type " + poll.pollType().name() + " is not yet supported by the Java voting GUI.");
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.vote_failed",
                            Map.of("reason", e.getMessage())));
                } catch (Exception e) {
                    sender.sendMessage(messages.format("errors.vote_failed",
                            Map.of("reason", e.getMessage() == null ? "Unexpected error." : e.getMessage())));
                }
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void handleShow(CommandSender sender, long pollId) throws PollServiceException {
        Poll poll = requirePoll(pollId);
        List<PollOption> options = findOptions(pollId);

        String statusColour = switch (poll.status()) {
            case DRAFT -> "§7";
            case READY -> "§e";
            case OPEN -> "§a";
            case CLOSED -> "§c";
            case ARCHIVED -> "§8";
            default -> "§f";
        };

        sender.sendMessage("§6Poll #" + poll.pollId() + " §f- §b" + poll.title());
        sender.sendMessage("§7Type: §f" + poll.pollType().name()
                + " §7| Status: " + statusColour + poll.status().name());

        if (!poll.description().isBlank()) {
            sender.sendMessage("§7Description: §f" + poll.description());
        } else {
            sender.sendMessage("§7Description: §8(blank)");
        }

        sender.sendMessage("§bRules:");
        sender.sendMessage(" §8- §7Max rankings: §f"
                + (poll.maxRankings() == 0 ? "ALL OPTIONS" : poll.maxRankings()));
        sender.sendMessage(" §8- §7Allow partial ranking: §f" + poll.allowPartialRanking());

        sender.sendMessage("§bOptions:");
        if (options.isEmpty()) {
            sender.sendMessage(" §8- §7No options configured.");
            return;
        }

        for (PollOption option : options) {
            sender.sendMessage(" §8- §f[" + option.optionId() + "] §b" + option.displayName()
                    + " §7(key=" + option.key() + ", order=" + option.displayOrder() + ")");

            if (!option.description().isBlank()) {
                sender.sendMessage("   §7" + option.description());
            }
        }
    }

    private void handleValidate(CommandSender sender, long pollId) throws PollServiceException {
        PollService.PollValidationResult result = pollService.validatePollDefinition(pollId);

        sender.sendMessage(messages.formatRaw("poll.validation_header", Map.of(
                "poll_id", String.valueOf(result.pollId()),
                "title", result.pollTitle()
        )));

        if (result.valid()) {
            sender.sendMessage(messages.get("poll.validation_ok"));
            return;
        }

        for (String issue : result.issues()) {
            sender.sendMessage(messages.formatRaw("poll.validation_issue", Map.of(
                    "issue", issue
            )));
        }
    }

    private void handleParticipationVerification(CommandSender sender,
                                                 Player player,
                                                 long pollId) throws PollServiceException {
        Poll poll = requirePoll(pollId);

        BallotService.VerificationResult inclusionResult = ballotService.verifyVoterInclusion(
                pollId,
                player.getUniqueId().toString()
        );

        IntegrityVerificationService.IntegrityVerificationResult integrityResult =
                integrityVerificationService.verifyPollIntegrity(pollId);

        sender.sendMessage(messages.formatRaw("verify.participation_header", Map.of(
                "poll_id", String.valueOf(pollId),
                "title", poll.title()
        )));

        if (inclusionResult.included()) {
            sender.sendMessage(messages.get("verify.included"));
        } else {
            sender.sendMessage(messages.get("verify.not_included"));
        }

        if (integrityResult.auditChainValid()) {
            sender.sendMessage(messages.get("verify.audit_valid"));
        } else {
            sender.sendMessage(messages.get("verify.audit_invalid"));
        }

        if (integrityResult.ballotHashesValid()) {
            sender.sendMessage(messages.get("verify.ballot_integrity_valid"));
        } else {
            sender.sendMessage(messages.get("verify.ballot_integrity_invalid"));
        }

        if (integrityResult.overallValid()) {
            sender.sendMessage(messages.get("verify.overall_valid"));
        } else {
            sender.sendMessage(messages.get("verify.overall_invalid"));
        }
    }

    private void handleBallotVerification(CommandSender sender,
                                          long pollId,
                                          String ballotProofPhrase) throws PollServiceException {
        Poll poll = requirePoll(pollId);

        BallotService.BallotProofVerificationResult verificationResult =
                ballotService.verifyBallotProof(pollId, ballotProofPhrase);

        sender.sendMessage(messages.formatRaw("verify.ballot_header", Map.of(
                "poll_id", String.valueOf(pollId),
                "title", poll.title()
        )));

        if (!verificationResult.ballotFound()) {
            sender.sendMessage("§cNo ballot matching that proof phrase was found for this poll.");
            sender.sendMessage("§7Check the poll ID and try the proof phrase again using the same words.");
            return;
        }

        if (verificationResult.ballotHashValid()) {
            sender.sendMessage(messages.get("verify.ballot_hash_valid"));
        } else {
            sender.sendMessage(messages.get("verify.ballot_hash_invalid"));
        }

        if (verificationResult.commitmentValid()) {
            sender.sendMessage(messages.get("verify.ballot_commitment_valid"));
        } else {
            sender.sendMessage(messages.get("verify.ballot_commitment_invalid"));
        }

        if (!verificationResult.overallValid()) {
            sender.sendMessage("§cThis proof phrase matched a stored ballot, but the stored ballot failed integrity checks.");
            sender.sendMessage("§cThat may indicate ballot data corruption or tampering and should be investigated.");
            return;
        }

        sender.sendMessage("§aThis ballot matches the original submission for this proof phrase.");
        sender.sendMessage("§7Verified ballot reference: §f" + verificationResult.ballotHash());

        Map<Long, String> optionNamesById = buildOptionNamesById(pollId);
        List<Long> orderedOptionIds = verificationResult.orderedOptionIds();

        if (poll.pollType() == PollType.YES_NO) {
            if (orderedOptionIds.isEmpty()) {
                sender.sendMessage("§eNo recorded selection was found for this ballot.");
                return;
            }

            long optionId = orderedOptionIds.get(0);
            String optionName = optionNamesById.getOrDefault(optionId, "Option #" + optionId);

            sender.sendMessage("§bVerified ballot selection:");
            sender.sendMessage(" §8- §f" + optionName);
            return;
        }

        sender.sendMessage("§bVerified ballot ranking:");
        for (int i = 0; i < orderedOptionIds.size(); i++) {
            long optionId = orderedOptionIds.get(i);
            String optionName = optionNamesById.getOrDefault(optionId, "Option #" + optionId);

            sender.sendMessage(" §8#§f" + (i + 1) + " §8-> §b" + optionName);
        }
    }

    private void handleResultDisplay(CommandSender sender,
                                     long pollId) throws PollServiceException {
        ResultService.PollResult result = resultService.getPollResult(pollId);

        for (String line : ResultDisplayFormatter.formatInGame(result)) {
            sender.sendMessage(line);
        }
    }

    private List<PollOption> findOptions(long pollId) throws PollServiceException {
        try {
            return pollOptionDao.findOptionsByPollId(pollId);
        } catch (Exception e) {
            throw new PollServiceException("Failed to load options for poll #" + pollId + ".", e);
        }
    }

    private Map<Long, String> buildOptionNamesById(long pollId) throws PollServiceException {
        try {
            List<PollOption> options = pollOptionDao.findOptionsByPollId(pollId);
            Map<Long, String> names = new HashMap<>();

            for (PollOption option : options) {
                names.put(option.optionId(), option.displayName());
            }

            return Map.copyOf(names);
        } catch (Exception e) {
            throw new PollServiceException("Failed to load options for poll #" + pollId + ".", e);
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6ModNVote 2.0 Commands");
        sender.sendMessage("§7Alias: §e/poll §7can be used instead of §e/" + label);
        sender.sendMessage("§e/" + label + " guide §7- How to create and manage polls");
        sender.sendMessage("§e/" + label + " create ranked_single_winner <optionCount> §7- Create a ranked poll with the GUI builder");
        sender.sendMessage("§e/" + label + " edit <draftPollId> §7- Resume editing a draft poll");
        sender.sendMessage("§e/" + label + " clone <sourcePollId> §7- Clone an existing poll into a new draft");
        sender.sendMessage("§e/" + label + " checkpoint <pollId> §7- Publish an integrity checkpoint");
        sender.sendMessage("§e/" + label + " list §7- List polls");
        sender.sendMessage("§e/" + label + " show <pollId> §7- Show poll details");
        sender.sendMessage("§e/" + label + " open <pollId> §7- Open a ready poll for voting");
        sender.sendMessage("§e/" + label + " close <pollId> §7- Close an open poll");
        sender.sendMessage("§e/" + label + " result <pollId> §7- Show results");
        sender.sendMessage("§e/" + label + " publishresult <pollId> §7- Republish a CLOSED poll result to witness webhooks.");
        sender.sendMessage("§e/" + label + " vote <pollId> §7- Vote in an open poll");
        sender.sendMessage("§e/" + label + " mypolls §7- Show polls you have participated in");
        sender.sendMessage("§e/" + label + " verify participation <pollId> §7- Verify your participation");
        sender.sendMessage("§e/" + label + " verify ballot <pollId> <proofPhrase> §7- Verify a ballot proof phrase");
    }

    private void sendGuide(CommandSender sender, String label) {
        sender.sendMessage("§6ModNVote Poll Builder Guide");
        sender.sendMessage("§7Alias: §e/poll §7can be used instead of §e/" + label);
        sender.sendMessage("§7Create a ranked poll:");
        sender.sendMessage("§e/" + label + " create ranked_single_winner <optionCount>");
        sender.sendMessage("§7Example:");
        sender.sendMessage("§e/" + label + " create ranked_single_winner 5");
        sender.sendMessage("§7The Poll Builder opens automatically.");
        sender.sendMessage("§7Left-click the title or option items to edit names.");
        sender.sendMessage("§7Right-click option items to edit descriptions.");
        sender.sendMessage("§7Click the description book to edit the poll description.");
        sender.sendMessage("§cRed fields §7still need work. §aGreen fields §7are complete.");
        sender.sendMessage("§7When READY turns green, click it to mark the poll ready.");
        sender.sendMessage("§7Resume an unfinished draft:");
        sender.sendMessage("§e/" + label + " edit <pollId>");
        sender.sendMessage("§7Clone an existing poll into a new editable draft:");
        sender.sendMessage("§e/" + label + " clone <sourcePollId>");
        sender.sendMessage("§7Publish an integrity checkpoint:");
        sender.sendMessage("§e/" + label + " checkpoint <pollId>");
    }

    private Poll requirePoll(long pollId) throws PollServiceException {
        Poll poll = pollService.findPollById(pollId);
        if (poll == null) {
            throw new PollServiceException(
                    messages.formatRaw("poll.not_found", Map.of("poll_id", String.valueOf(pollId)))
            );
        }
        return poll;
    }

    private long parsePollId(String raw) throws PollServiceException {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new PollServiceException(messages.getRaw("poll.invalid_id"));
        }
    }

    private int parseOptionCount(String raw) throws PollServiceException {
        try {
            int optionCount = Integer.parseInt(raw);
            if (optionCount < 2) {
                throw new PollServiceException("Ranked polls must have at least 2 options.");
            }
            if (optionCount > 30) {
                throw new PollServiceException("Ranked poll builder currently supports at most 30 options.");
            }
            return optionCount;
        } catch (NumberFormatException e) {
            throw new PollServiceException("Option count must be a whole number.");
        }
    }

    private PollType parsePollType(String raw) throws PollServiceException {
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        try {
            return PollType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new PollServiceException("Unsupported poll type '" + raw + "'. Use YES_NO or RANKED_SINGLE_WINNER.");
        }
    }

    private boolean parseBoolean(String raw, String fieldName) throws PollServiceException {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new PollServiceException(fieldName + " must be true or false.");
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private String[] splitNameAndDescription(String joined) throws PollServiceException {
        int separator = joined.indexOf('|');
        if (separator < 0) {
            throw new PollServiceException("Option add requires '<displayName> | <description>'.");
        }

        String displayName = joined.substring(0, separator).trim();
        String description = joined.substring(separator + 1).trim();

        if (displayName.isBlank()) {
            throw new PollServiceException("Option display name must not be blank.");
        }

        return new String[] {displayName, description};
    }

    private String normalizeBallotProofPhrase(String raw) throws PollServiceException {
        Objects.requireNonNull(raw, "raw");

        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isBlank()) {
            throw new PollServiceException("Ballot proof phrase must not be blank.");
        }

        String normalizedWhitespace = trimmed.replaceAll("\\s+", " ");
        return normalizedWhitespace.replace(' ', '-');
    }

    private boolean isPollIdArgumentPosition(String[] args, int index, CommandSender sender) {
        if (args.length <= index) {
            return false;
        }

        String root = args[0].toLowerCase(Locale.ROOT);

        if ("show".equals(root) || "validate".equals(root) || "ready".equals(root)
                || "open".equals(root) || "close".equals(root)
                || "result".equals(root) || "publishresult".equals(root)
                || "vote".equals(root)
                || "clone".equals(root) || "checkpoint".equals(root)) {
            return index == 1;
        }

        if (("set".equals(root) || "delete".equals(root)) && sender.hasPermission("modnvote.admin.poll.create")) {
            return index == 1;
        }

        if ("option".equals(root) && sender.hasPermission("modnvote.admin.poll.create")) {
            return index == 2;
        }

        if ("verify".equals(root) && sender.hasPermission("modnvote.verify")) {
            if (args.length == 2) {
                return index == 1;
            }
            if (args.length >= 3 && "participation".equalsIgnoreCase(args[1])) {
                return index == 2;
            }
            if (args.length >= 4 && "ballot".equalsIgnoreCase(args[1])) {
                return index == 2;
            }
        }

        return false;
    }

    private List<String> loadPollIdCompletions() {
        return loadPollIdCompletions((PollStatus) null);
    }

    private List<String> loadPollIdCompletions(PollStatus requiredStatus) {
        try {
            List<Poll> polls = pollService.listPolls();
            List<String> out = new ArrayList<>(polls.size());

            for (Poll poll : polls) {
                if (requiredStatus == null || poll.status() == requiredStatus) {
                    out.add(String.valueOf(poll.pollId()));
                }
            }

            return out;
        } catch (PollServiceException e) {
            return Collections.emptyList();
        }
    }

    private List<String> loadPollIdCompletions(List<PollStatus> requiredStatuses) {
        try {
            List<Poll> polls = pollService.listPolls();
            List<String> out = new ArrayList<>(polls.size());

            for (Poll poll : polls) {
                if (requiredStatuses.contains(poll.status())) {
                    out.add(String.valueOf(poll.pollId()));
                }
            }

            return out;
        } catch (PollServiceException e) {
            return Collections.emptyList();
        }
    }

    private List<String> loadPollIdCompletionsForRoot(String rootCommand) {
        String root = rootCommand.toLowerCase(Locale.ROOT);

        return switch (root) {
            case "open" -> loadPollIdCompletions(PollStatus.READY);
            case "close" -> loadPollIdCompletions(PollStatus.OPEN);
            case "result", "publishresult" -> loadPollIdCompletions(PollStatus.CLOSED);
            case "vote" -> loadPollIdCompletions(PollStatus.OPEN);
            case "edit", "validate", "ready", "set", "option" -> loadPollIdCompletions(PollStatus.DRAFT);
            case "delete" -> loadPollIdCompletions(List.of(PollStatus.DRAFT, PollStatus.READY));
            case "show", "clone", "checkpoint" -> loadPollIdCompletions();
            default -> loadPollIdCompletions();
        };
    }

    private List<String> loadOptionIdCompletions(long pollId) {
        try {
            List<PollOption> options = pollOptionDao.findOptionsByPollId(pollId);
            List<String> out = new ArrayList<>(options.size());

            for (PollOption option : options) {
                out.add(String.valueOf(option.optionId()));
            }

            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private long tryParsePollId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private List<String> filterCompletions(List<String> candidates, String token) {
        String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                out.add(candidate);
            }
        }

        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("status");

            if (sender.hasPermission("modnvote.admin.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("modnvote.admin.poll.list")) {
                completions.add("list");
            }
            if (sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("create");
                completions.add("clone");
                completions.add("checkpoint");
                completions.add("edit");
                completions.add("guide");
                completions.add("show");
                completions.add("delete");
            }
            if (sender.hasPermission("modnvote.admin.poll.open")) {
                completions.add("open");
            }
            if (sender.hasPermission("modnvote.admin.poll.close")) {
                completions.add("close");
                completions.add("publishresult");
            }

            completions.add("result");

            if (sender.hasPermission("modnvote.verify")) {
                completions.add("mypolls");
                completions.add("verify");
            }

            completions.add("vote");

            if (sender.hasPermission("modnvote.testvote")) {
                completions.add("testvote");
            }

            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("verify") && sender.hasPermission("modnvote.verify")) {
                completions.add("participation");
                completions.add("ballot");
                return filterCompletions(completions, args[1]);
            }

            if (args[0].equalsIgnoreCase("create") && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("yes_no");
                completions.add("ranked_single_winner");
                return filterCompletions(completions, args[1]);
            }

            if (args[0].equalsIgnoreCase("edit") && sender.hasPermission("modnvote.admin.poll.create")) {
                return filterCompletions(loadPollIdCompletions(PollStatus.DRAFT), args[1]);
            }

            if (args[0].equalsIgnoreCase("set") && sender.hasPermission("modnvote.admin.poll.create")) {
                return filterCompletions(loadPollIdCompletions(PollStatus.DRAFT), args[1]);
            }

            if (args[0].equalsIgnoreCase("delete") && sender.hasPermission("modnvote.admin.poll.create")) {
                return filterCompletions(loadPollIdCompletions(List.of(PollStatus.DRAFT, PollStatus.READY)), args[1]);
            }

            if (args[0].equalsIgnoreCase("option") && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("add");
                completions.add("edit");
                completions.add("move");
                completions.add("remove");
                return filterCompletions(completions, args[1]);
            }

            if (isPollIdArgumentPosition(args, 1, sender)) {
                return filterCompletions(loadPollIdCompletionsForRoot(args[0]), args[1]);
            }

            return Collections.emptyList();
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")
                    && args[1].equalsIgnoreCase("ranked_single_winner")
                    && sender.hasPermission("modnvote.admin.poll.create")) {
                return filterCompletions(List.of("2", "3", "4", "5", "6"), args[2]);
            }

            if (args[0].equalsIgnoreCase("set")
                    && sender.hasPermission("modnvote.admin.poll.create")) {
                return filterCompletions(
                        List.of("title", "description", "maxrankings", "allowpartial"),
                        args[2]
                );
            }

            if (args[0].equalsIgnoreCase("poll") && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("title");
                completions.add("description");
                completions.add("maxrankings");
                completions.add("allowpartial");
                return filterCompletions(completions, args[2]);
            }

            if (args[0].equalsIgnoreCase("option")
                    && sender.hasPermission("modnvote.admin.poll.create")
                    && ("edit".equalsIgnoreCase(args[1])
                    || "move".equalsIgnoreCase(args[1])
                    || "remove".equalsIgnoreCase(args[1])
                    || "add".equalsIgnoreCase(args[1]))) {
                return filterCompletions(loadPollIdCompletions(PollStatus.DRAFT), args[2]);
            }

            if (args[0].equalsIgnoreCase("verify")
                    && "participation".equalsIgnoreCase(args[1])
                    && sender.hasPermission("modnvote.verify")) {
                return filterCompletions(loadPollIdCompletions(), args[2]);
            }

            if (args[0].equalsIgnoreCase("vote")) {
                return filterCompletions(loadPollIdCompletions(PollStatus.OPEN), args[1]);
            }

            if (args[0].equalsIgnoreCase("result")) {
                return filterCompletions(loadPollIdCompletions(PollStatus.CLOSED), args[1]);
            }

            return Collections.emptyList();
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("verify")
                    && "ballot".equalsIgnoreCase(args[1])
                    && sender.hasPermission("modnvote.verify")) {
                return Collections.emptyList();
            }

            if (args[0].equalsIgnoreCase("option")
                    && sender.hasPermission("modnvote.admin.poll.create")) {
                if ("edit".equalsIgnoreCase(args[1])
                        || "move".equalsIgnoreCase(args[1])
                        || "remove".equalsIgnoreCase(args[1])) {
                    long pollId = tryParsePollId(args[2]);
                    if (pollId > 0) {
                        return filterCompletions(loadOptionIdCompletions(pollId), args[3]);
                    }
                }
            }

            return Collections.emptyList();
        }

        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("option")
                    && "edit".equalsIgnoreCase(args[1])
                    && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("name");
                completions.add("description");
                return filterCompletions(completions, args[4]);
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }
}