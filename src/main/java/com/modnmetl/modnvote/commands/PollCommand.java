package com.modnmetl.modnvote.commands;

import com.modnmetl.modnvote.ModNVotePlugin;
import com.modnmetl.modnvote.config.MessageService;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.IntegrityVerificationService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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
 * - integrity reporting combines inclusion checks with deeper ballot verification
 */
public final class PollCommand implements CommandExecutor, TabCompleter {

    private final ModNVotePlugin plugin;
    private final PollService pollService;
    private final BallotService ballotService;
    private final IntegrityVerificationService integrityVerificationService;
    private final MessageService messages;

    public PollCommand(ModNVotePlugin plugin,
                       PollService pollService,
                       BallotService ballotService,
                       IntegrityVerificationService integrityVerificationService,
                       MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pollService = Objects.requireNonNull(pollService, "pollService");
        this.ballotService = Objects.requireNonNull(ballotService, "ballotService");
        this.integrityVerificationService = Objects.requireNonNull(integrityVerificationService, "integrityVerificationService");
        this.messages = Objects.requireNonNull(messages, "messages");
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
                    sender.sendMessage(messages.format("usage.verify", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    Poll poll = requirePoll(pollId);

                    BallotService.VerificationResult inclusionResult = ballotService.verifyVoterInclusion(
                            pollId,
                            player.getUniqueId().toString()
                    );

                    IntegrityVerificationService.IntegrityVerificationResult integrityResult =
                            integrityVerificationService.verifyPollIntegrity(pollId);

                    sender.sendMessage(messages.formatRaw("verify.header", Map.of(
                            "poll_id", String.valueOf(pollId),
                            "title", poll.title()
                    )));

                    if (inclusionResult.included()) {
                        sender.sendMessage(messages.get("verify.included"));
                    } else {
                        sender.sendMessage(messages.get("verify.not_included"));
                    }

                    if (inclusionResult.receiptBackedByAnonymousBallot()) {
                        sender.sendMessage(messages.get("verify.receipt_backed"));
                    } else if (inclusionResult.included()) {
                        sender.sendMessage(messages.get("verify.receipt_missing"));
                    }

                    if (inclusionResult.auditChainValid()) {
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

                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.verify_failed",
                            Map.of("reason", e.getMessage())));
                }
                return true;
            }
            case "testvote" -> {
                if (!sender.hasPermission("modnvote.testvote")) {
                    sender.sendMessage(messages.get("general.no_permission"));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("general.players_only"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.format("usage.testvote", Map.of("label", label)));
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);

                    List<Long> rankedOptionIds = new ArrayList<>();
                    for (int i = 2; i < args.length; i++) {
                        try {
                            rankedOptionIds.add(Long.parseLong(args[i]));
                        } catch (NumberFormatException e) {
                            throw new PollServiceException("Option IDs must be whole numbers.");
                        }
                    }

                    String ipHash = hashPlayerIp(player);
                    if (ipHash == null) {
                        throw new PollServiceException("We could not confirm your network address for duplicate-protection checks.");
                    }

                    String bypassNode = plugin.getConfig().getString("permissions.bypass_node", "modnvote.bypass");
                    boolean bypassIpDuplicateCheck = player.hasPermission(bypassNode);

                    BallotService.SubmissionResult result = ballotService.submitRankedBallot(
                            pollId,
                            player.getUniqueId().toString(),
                            "TEST_COMMAND",
                            rankedOptionIds,
                            ipHash,
                            null,
                            bypassIpDuplicateCheck
                    );

                    sender.sendMessage(messages.get("vote.submit_success"));
                    sender.sendMessage(messages.get("vote.education_privacy"));
                    sender.sendMessage(messages.get("vote.education_verification"));
                    sender.sendMessage(messages.formatRaw("vote.ballot_hash",
                            Map.of("ballot_hash", result.ballotHash())));
                    sender.sendMessage(messages.formatRaw("vote.receipt_hash",
                            Map.of("receipt_hash", result.receiptHash())));

                    if (bypassIpDuplicateCheck) {
                        sender.sendMessage(messages.get("vote.bypass_used"));
                    }

                } catch (PollServiceException e) {
                    sender.sendMessage(messages.format("errors.vote_failed",
                            Map.of("reason", e.getMessage())));
                }

                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(messages.getRaw("help.header"));
        sender.sendMessage(messages.formatRaw("help.status", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.reload", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.list", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.seedbreed", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.open", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.close", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.verify", Map.of("label", label)));
        sender.sendMessage(messages.formatRaw("help.testvote", Map.of("label", label)));
    }

    private Poll requirePoll(long pollId) throws PollServiceException {
        Poll poll = pollService.findPollById(pollId);
        if (poll == null) {
            throw new PollServiceException("Poll #" + pollId + " does not exist.");
        }
        return poll;
    }

    private long parsePollId(String raw) throws PollServiceException {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new PollServiceException("Poll IDs must be whole numbers.");
        }
    }

    private String hashPlayerIp(Player player) {
        try {
            InetSocketAddress address = player.getAddress();
            if (address == null || address.getAddress() == null) {
                return null;
            }

            String hostAddress = address.getAddress().getHostAddress();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(hostAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return null;
        }
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
                completions.add("seedbreed");
            }
            if (sender.hasPermission("modnvote.admin.poll.open")) {
                completions.add("open");
            }
            if (sender.hasPermission("modnvote.admin.poll.close")) {
                completions.add("close");
            }
            if (sender.hasPermission("modnvote.verify")) {
                completions.add("verify");
            }
            if (sender.hasPermission("modnvote.testvote")) {
                completions.add("testvote");
            }
        }

        return completions;
    }
}