package com.modnmetl.modnvote.commands;

import com.modnmetl.modnvote.ModNVotePlugin;
import com.modnmetl.modnvote.service.BallotService;
import com.modnmetl.modnvote.service.PollService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Temporary 2.0 root command scaffold.
 *
 * This replaces the old 1.x command tree with a poll-oriented command entrypoint.
 * The current implementation is intentionally small so the new runtime can boot,
 * be tested, and then extended incrementally.
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
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("status");
            if (sender.hasPermission("modnvote.admin.reload")) {
                completions.add("reload");
            }
        }

        return completions;
    }
}