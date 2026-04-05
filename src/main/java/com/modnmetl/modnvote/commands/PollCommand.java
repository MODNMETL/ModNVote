package com.modnmetl.modnvote.commands;

import com.modnmetl.modnvote.ModNVotePlugin;
import com.modnmetl.modnvote.api.PollStatus;
import com.modnmetl.modnvote.api.PollType;
import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.domain.PollOption;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.IntegrityVerificationService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import com.modnmetl.modnvote.service.ResultService;
import com.modnmetl.modnvote.storage.PollOptionDao;
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
                sender.sendMessage(messages.formatRaw("status.database",
                        Map.of("path", plugin.getDatabaseManager().getDatabasePath().toAbsolutePath().toString())));
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
                if (args.length < 3) {
                    sender.sendMessage(messages.format("usage.create", Map.of("label", label)));
                    return true;
                }

                try {
                    PollType pollType = parsePollType(args[1]);
                    String slug = args[2];

                    long pollId = pollService.createPoll(sender.getName(), pollType, slug);
                    Poll poll = requirePoll(pollId);

                    sender.sendMessage(messages.format("poll.created", Map.of(
                            "poll_id", String.valueOf(pollId),
                            "title", poll.title(),
                            "type", poll.pollType().name()
                    )));
                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.create_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "seedbreed" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }

                try {
                    long pollId = pollService.createSeedBreedPoll(sender.getName());
                    Poll poll = pollService.findPollById(pollId);

                    String title = poll != null ? poll.title() : "Seed Breed Poll";

                    sender.sendMessage(messages.format("poll.seedbreed_created", Map.of(
                            "poll_id", String.valueOf(pollId),
                            "title", title
                    )));
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
            case "poll" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(messages.format("usage.poll_title", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    String action = args[2].toLowerCase(Locale.ROOT);

                    switch (action) {
                        case "title" -> {
                            String title = joinArgs(args, 3);
                            pollService.updatePollTitle(pollId, title, sender.getName());
                            sender.sendMessage(messages.format("poll.updated_title", Map.of(
                                    "poll_id", String.valueOf(pollId),
                                    "title", title
                            )));
                        }
                        case "description" -> {
                            String description = joinArgs(args, 3);
                            pollService.updatePollDescription(pollId, description, sender.getName());
                            sender.sendMessage(messages.format("poll.updated_description", Map.of(
                                    "poll_id", String.valueOf(pollId)
                            )));
                        }
                        case "maxrankings" -> {
                            int maxRankings = Integer.parseInt(args[3]);
                            pollService.updatePollMaxRankings(pollId, maxRankings, sender.getName());
                            sender.sendMessage(messages.format("poll.updated_max_rankings", Map.of(
                                    "poll_id", String.valueOf(pollId),
                                    "max_rankings", String.valueOf(maxRankings)
                            )));
                        }
                        case "allowpartial" -> {
                            boolean allowPartial = parseBoolean(args[3], "allowpartial");
                            pollService.updatePollAllowPartialRanking(pollId, allowPartial, sender.getName());
                            sender.sendMessage(messages.format("poll.updated_allow_partial", Map.of(
                                    "poll_id", String.valueOf(pollId),
                                    "allow_partial", String.valueOf(allowPartial)
                            )));
                        }
                        default -> sender.sendMessage(messages.format("usage.poll_title", Map.of("label", label)));
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
                            ballotService.listParticipatedPolls(player.getUniqueId().toString());

                    if (summaries.isEmpty()) {
                        sender.sendMessage(messages.get("mypolls.empty"));
                        return true;
                    }

                    sender.sendMessage(messages.getRaw("mypolls.header"));
                    for (BallotService.ParticipationSummary summary : summaries) {
                        sender.sendMessage(messages.formatRaw("mypolls.entry", Map.of(
                                "poll_id", String.valueOf(summary.pollId()),
                                "title", summary.pollTitle(),
                                "status", summary.pollStatus()
                        )));
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
                        String ballotProofPhrase = args[3];
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

        sender.sendMessage(messages.formatRaw("poll.show_header", Map.of(
                "poll_id", String.valueOf(poll.pollId()),
                "title", poll.title()
        )));
        sender.sendMessage(messages.formatRaw("poll.show_description", Map.of(
                "description", poll.description().isBlank() ? "(blank)" : poll.description()
        )));
        sender.sendMessage(messages.formatRaw("poll.show_type", Map.of(
                "type", poll.pollType().name()
        )));
        sender.sendMessage(messages.formatRaw("poll.show_status", Map.of(
                "status", poll.status().name()
        )));

        sender.sendMessage(messages.getRaw("poll.show_rules_header"));
        sender.sendMessage(messages.formatRaw("poll.show_rule_max_rankings", Map.of(
                "max_rankings", poll.maxRankings() == 0 ? "ALL OPTIONS" : String.valueOf(poll.maxRankings())
        )));
        sender.sendMessage(messages.formatRaw("poll.show_rule_allow_partial", Map.of(
                "allow_partial", String.valueOf(poll.allowPartialRanking())
        )));

        sender.sendMessage(messages.getRaw("poll.show_options_header"));
        if (options.isEmpty()) {
            sender.sendMessage(messages.get("poll.show_options_empty"));
            return;
        }

        for (PollOption option : options) {
            sender.sendMessage(messages.formatRaw("poll.show_option_entry", Map.of(
                    "option_id", String.valueOf(option.optionId()),
                    "option_key", option.key(),
                    "option_name", option.displayName(),
                    "description", option.description(),
                    "display_order", String.valueOf(option.displayOrder())
            )));
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
            sender.sendMessage(messages.get("verify.ballot_not_found"));
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
            return;
        }

        sender.sendMessage(messages.getRaw("verify.ballot_selection_header"));

        Map<Long, String> optionNamesById = buildOptionNamesById(pollId);
        List<Long> orderedOptionIds = verificationResult.orderedOptionIds();

        for (int i = 0; i < orderedOptionIds.size(); i++) {
            long optionId = orderedOptionIds.get(i);
            String optionName = optionNamesById.getOrDefault(optionId, "Option #" + optionId);

            sender.sendMessage(messages.formatRaw("verify.ballot_selection_entry", Map.of(
                    "rank", String.valueOf(i + 1),
                    "option_name", optionName
            )));
        }
    }

    private void handleResultDisplay(CommandSender sender,
                                     long pollId) throws PollServiceException {
        ResultService.PollResult result = resultService.getPollResult(pollId);

        sender.sendMessage(messages.formatRaw("result.header", Map.of(
                "poll_id", String.valueOf(result.pollId()),
                "title", result.pollTitle()
        )));
        sender.sendMessage(messages.formatRaw("result.total_votes", Map.of(
                "total_votes", String.valueOf(result.totalVotes())
        )));

        if (result.pollType() == PollType.YES_NO) {
            Map<String, Integer> countsByName = new HashMap<>();
            for (ResultService.OptionTally tally : result.tallies()) {
                countsByName.put(tally.optionName().toLowerCase(Locale.ROOT), tally.votes());
            }

            int yesVotes = countsByName.getOrDefault("yes", 0);
            int noVotes = countsByName.getOrDefault("no", 0);

            sender.sendMessage(messages.formatRaw("result.yes_votes", Map.of(
                    "yes_votes", String.valueOf(yesVotes)
            )));
            sender.sendMessage(messages.formatRaw("result.no_votes", Map.of(
                    "no_votes", String.valueOf(noVotes)
            )));
            return;
        }

        if (result.pollType() == PollType.RANKED_SINGLE_WINNER) {
            sender.sendMessage(messages.formatRaw("result.ranked_winner", Map.of(
                    "winner", result.winnerName() == null ? "No winner determined" : result.winnerName()
            )));
            sender.sendMessage(messages.getRaw("result.first_preference_header"));

            for (ResultService.OptionTally tally : result.tallies()) {
                sender.sendMessage(messages.formatRaw("result.tally_entry", Map.of(
                        "option_name", tally.optionName(),
                        "votes", String.valueOf(tally.votes())
                )));
            }
            return;
        }

        sender.sendMessage(messages.get("result.unsupported_type"));
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
        sender.sendMessage(messages.getRaw("help.header"));
        sender.sendMessage(messages.formatRaw("help.status", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.reload", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.list", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.create", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.seedbreed", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.show", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.poll", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.option", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.validate", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.ready", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.open", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.close", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.result", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.mypolls", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.verify_participation", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.verify_ballot", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.vote", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.testvote", Map.of("label", label)));
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
                completions.add("seedbreed");
                completions.add("show");
                completions.add("poll");
                completions.add("option");
                completions.add("validate");
                completions.add("ready");
            }
            if (sender.hasPermission("modnvote.admin.poll.open")) {
                completions.add("open");
            }
            if (sender.hasPermission("modnvote.admin.poll.close")) {
                completions.add("close");
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
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("verify") && sender.hasPermission("modnvote.verify")) {
                completions.add("participation");
                completions.add("ballot");
            }
            if (args[0].equalsIgnoreCase("create") && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("yes_no");
                completions.add("ranked_single_winner");
            }
            if (args[0].equalsIgnoreCase("poll") && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("<pollId>");
            }
            if (args[0].equalsIgnoreCase("option") && sender.hasPermission("modnvote.admin.poll.create")) {
                completions.add("add");
                completions.add("edit");
                completions.add("move");
                completions.add("remove");
            }
        } else if (args.length == 3
                && args[0].equalsIgnoreCase("poll")
                && sender.hasPermission("modnvote.admin.poll.create")) {
            completions.add("title");
            completions.add("description");
            completions.add("maxrankings");
            completions.add("allowpartial");
        } else if (args.length == 5
                && args[0].equalsIgnoreCase("option")
                && "edit".equalsIgnoreCase(args[1])
                && sender.hasPermission("modnvote.admin.poll.create")) {
            completions.add("name");
            completions.add("description");
        }

        return completions;
    }
}