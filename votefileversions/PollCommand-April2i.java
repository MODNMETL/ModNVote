package com.modnmetl.modnvote.commands;

import com.modnmetl.modnvote.ModNVotePlugin;
import com.modnmetl.modnvote.domain.Poll;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.PollService;
import com.modnmetl.modnvote.service.PollServiceException;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Temporary 2.0 root command scaffold.
 */
public final class PollCommand implements CommandExecutor, TabCompleter {

    private final ModNVotePlugin plugin;
    private final PollService pollService;
    private final BallotService ballotService;

    public PollCommand(ModNVotePlugin plugin,
                       PollService pollService,
                       BallotService ballotService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pollService = Objects.requireNonNull(pollService, "pollService");
        this.ballotService = Objects.requireNonNull(ballotService, "ballotService");
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
                sender.sendMessage(ChatColor.GOLD + "ModNVote 2.0");
                sender.sendMessage(ChatColor.YELLOW + "- " + pollService.getStatusSummary());
                sender.sendMessage(ChatColor.YELLOW + "- " + ballotService.getStatusSummary());
                sender.sendMessage(ChatColor.YELLOW + "- Database: "
                        + plugin.getDatabaseManager().getDatabasePath().toAbsolutePath());
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("modnvote.admin.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                plugin.reloadConfig();
                String message = plugin.getConfig().getString("messages.reloaded", "&aConfiguration reloaded.");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("modnvote.admin.poll.list")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                try {
                    List<Poll> polls = pollService.listPolls();
                    if (polls.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "No polls currently exist.");
                        return true;
                    }

                    sender.sendMessage(ChatColor.GOLD + "Stored polls:");
                    for (Poll poll : polls) {
                        sender.sendMessage(ChatColor.YELLOW + "- #" + poll.pollId()
                                + " [" + poll.status().name() + "] "
                                + poll.slug() + " :: " + poll.title()
                                + " (" + poll.pollType().name() + ")");
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to list polls: " + e.getMessage());
                }
                return true;
            }
            case "seedbreed" -> {
                if (!sender.hasPermission("modnvote.admin.poll.create")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                try {
                    long pollId = pollService.createSeedBreedPoll(sender.getName());
                    sender.sendMessage(ChatColor.GREEN + "Created seed breed poll with ID " + pollId + ".");
                } catch (PollServiceException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to create seed breed poll: " + e.getMessage());
                }
                return true;
            }
            case "open" -> {
                if (!sender.hasPermission("modnvote.admin.poll.open")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " open <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    pollService.openPoll(pollId, sender.getName());
                    sender.sendMessage(ChatColor.GREEN + "Opened poll #" + pollId + ".");
                } catch (PollServiceException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to open poll: " + e.getMessage());
                }
                return true;
            }
            case "close" -> {
                if (!sender.hasPermission("modnvote.admin.poll.close")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " close <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    pollService.closePoll(pollId, sender.getName());
                    sender.sendMessage(ChatColor.GREEN + "Closed poll #" + pollId + ".");
                } catch (PollServiceException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to close poll: " + e.getMessage());
                }
                return true;
            }
            case "verify" -> {
                if (!sender.hasPermission("modnvote.verify")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " verify <pollId>");
                    return true;
                }

                try {
                    long pollId = parsePollId(args[1]);
                    BallotService.VerificationResult result = ballotService.verifyVoterInclusion(
                            pollId,
                            player.getUniqueId().toString()
                    );

                    sender.sendMessage(ChatColor.GOLD + "Verification for poll #" + pollId + ":");

                    if (result.included()) {
                        sender.sendMessage(ChatColor.GREEN + "- Your vote is included in this poll.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "- No recorded vote from you was found in this poll.");
                    }

                    if (result.receiptBackedByAnonymousBallot()) {
                        sender.sendMessage(ChatColor.GREEN + "- Your recorded participation is backed by an anonymous ballot.");
                    } else if (result.included()) {
                        sender.sendMessage(ChatColor.RED + "- Your recorded participation is not backed by an anonymous ballot.");
                    }

                    if (result.auditChainValid()) {
                        sender.sendMessage(ChatColor.GREEN + "- The poll audit chain is currently valid.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "- The poll audit chain is INVALID.");
                    }
                } catch (PollServiceException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to verify poll inclusion: " + e.getMessage());
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
        sender.sendMessage(ChatColor.GOLD + "ModNVote 2.0 Commands");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " seedbreed");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " open <pollId>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " close <pollId>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " verify <pollId>");
    }

    private long parsePollId(String raw) throws PollServiceException {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new PollServiceException("Poll id must be a whole number.");
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
        }

        return completions;
    }
}