package com.modnmetl.modnvote.commands;

import com.modnmetl.modnvote.ModNVotePlugin;
import com.modnmetl.modnvote.storage.VoteChoice;
import com.modnmetl.modnvote.storage.VoteDao;
import com.modnmetl.modnvote.util.Integrity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class ModNVoteCommand implements CommandExecutor, TabCompleter {
    private final ModNVotePlugin plugin;
    private final VoteDao dao;
    private final AtomicInteger yesCache;
    private final AtomicInteger noCache;
    private final ExecutorService dbPool;

    public ModNVoteCommand(ModNVotePlugin plugin, VoteDao dao, AtomicInteger yesCache, AtomicInteger noCache, ExecutorService dbPool) {
        this.plugin = plugin;
        this.dao = dao;
        this.yesCache = yesCache;
        this.noCache = noCache;
        this.dbPool = dbPool;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <yes|no|status|verify|reset|reload|audit|fullaudit>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "yes":
            case "no":
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Only players can vote.");
                    return true;
                }
                if (!sender.hasPermission("modnvote.vote")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to vote.");
                    return true;
                }
                handleVote((Player) sender, VoteChoice.fromString(sub));
                return true;

            case "status":
                if (!sender.hasPermission("modnvote.status")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "ModNVote » " + ChatColor.GREEN + "YES: " + yesCache.get() + ChatColor.GRAY + " | " + ChatColor.RED + "NO: " + noCache.get());
                return true;

            case "reset":
                if (!sender.hasPermission("modnvote.admin.reset")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                dbPool.execute(() -> {
                    try {
                        dao.resetAll(plugin.getRoundId());
                        plugin.refreshCaches();
                        sender.sendMessage(ChatColor.YELLOW + plugin.msg("messages.reset_done", "All votes reset."));
                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "[ModNVote] Reset failed: " + e.getMessage());
                    }
                });
                return true;

            case "reload":
                if (!sender.hasPermission("modnvote.admin.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                plugin.reloadConfig();
                plugin.loadConfigValues();
                sender.sendMessage(ChatColor.YELLOW + plugin.msg("messages.reloaded", "ModNVote configuration reloaded."));
                return true;

            case "audit":
                if (!sender.hasPermission("modnvote.admin.audit")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                dbPool.execute(() -> {
                    try {
                        int total = dao.countParticipants(plugin.getRoundId());
                        int bypass = dao.countBypass(plugin.getRoundId());
                        int[] t = dao.getTally(plugin.getRoundId());
                        double pct = total == 0 ? 0.0 : (bypass * 100.0 / total);
                        String msg = plugin.msg("messages.audit_summary",
                                "Audit » Total: {total}, With bypass: {bypass} ({percent}%) | YES: {yes}, NO: {no}")
                                .replace("{total}", Integer.toString(total))
                                .replace("{bypass}", Integer.toString(bypass))
                                .replace("{percent}", String.format(java.util.Locale.ROOT, "%.1f", pct))
                                .replace("{yes}", Integer.toString(t[0]))
                                .replace("{no}", Integer.toString(t[1]));
                        sender.sendMessage(ChatColor.GOLD + msg);
                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "[ModNVote] Audit failed: " + e.getMessage());
                    }
                });
                return true;

            case "fullaudit":
                if (!sender.hasPermission("modnvote.admin.fullaudit")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + plugin.msg("messages.fullaudit_header",
                        "Full audit (grouped by IP). Showing voters with bypass; non-bypass on same IP listed under each group."));
                dbPool.execute(() -> runFullAudit(sender));
                return true;

            case "verify":
                if (!sender.hasPermission("modnvote.verify")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                dbPool.execute(() -> {
                    try {
                        int r = plugin.getRoundId();
                        int[] t = dao.getTally(r);
                        List<UUID> ids = dao.fetchAllUuids(r);
                        String canon = Integrity.canonicalString(r, t[0], t[1], ids);
                        String expected = Integrity.hmacSha256Hex(plugin.getPepper(), canon);
                        String stored = dao.getStoredHmac(r);
                        boolean ok = expected.equalsIgnoreCase(stored);
                        sender.sendMessage(ok
                                ? ChatColor.GREEN + plugin.msg("messages.verify_valid", "Verification passed: tally matches participant set.")
                                : ChatColor.RED + plugin.msg("messages.verify_invalid", "Verification failed: tally does NOT match participant set."));
                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "[ModNVote] Verify failed: " + e.getMessage());
                    }
                });
                return true;

            default:
                sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <yes|no|status|verify|reset|reload|audit|fullaudit>");
                return true;
        }
    }

    private void handleVote(Player p, VoteChoice choice) {
        if (choice == null) {
            p.sendMessage(ChatColor.RED + "Usage: /modnvote <yes|no>");
            return;
        }

        final UUID uuid = p.getUniqueId();
        final String ip = resolvePlayerIp(p);
        final boolean hasBypass = p.hasPermission(plugin.getBypassPermissionNode());

        dbPool.execute(() -> {
            try {
                int round = plugin.getRoundId();
                if (dao.hasUuidVoted(uuid, round)) {
                    p.sendMessage(ChatColor.RED + plugin.msg("messages.already_voted", "You have already voted."));
                    return;
                }
                if (!hasBypass && ip != null && dao.hasIpVoted(ip, round)) {
                    p.sendMessage(ChatColor.RED + plugin.msg("messages.duplicate_ip", "A vote from your location has already been recorded."));
                    return;
                }

                dao.insertParticipation(uuid, ip == null ? "unknown" : ip, hasBypass, round);

                int[] t = dao.getTally(round);
                int yes = t[0], no = t[1];
                if (choice == VoteChoice.YES) {
                    yes++;
                    p.sendMessage(ChatColor.GREEN + plugin.msg("messages.voted_yes", "Thanks — your YES vote has been recorded."));
                } else {
                    no++;
                    p.sendMessage(ChatColor.GREEN + plugin.msg("messages.voted_no", "Thanks — your NO vote has been recorded."));
                }

                List<UUID> ids = dao.fetchAllUuids(round);
                String canon = Integrity.canonicalString(round, yes, no, ids);
                String hmac = Integrity.hmacSha256Hex(plugin.getPepper(), canon);
                dao.updateTally(round, yes, no, hmac);

                yesCache.set(yes);
                noCache.set(no);

                if (plugin.isAuditVotesToConsole()) {
                    plugin.getLogger().info("Vote recorded for " + p.getName() + " (bypass=" + hasBypass + ") from IP " + ip);
                }
                if (hasBypass && plugin.isAuditBypassToConsole()) {
                    plugin.getLogger().info("[BYPASS] " + p.getName() + " voted with bypass permission (" + plugin.getBypassPermissionNode() + ")");
                }

            } catch (Exception e) {
                p.sendMessage(ChatColor.RED + "Sorry, something went wrong recording your vote. Please try again.");
                plugin.getLogger().severe("Vote failed for " + p.getName() + ": " + e.getMessage());
            }
        });
    }

    private String resolvePlayerIp(Player p) {
        try {
            InetSocketAddress addr = p.getAddress();
            if (addr == null) return null;
            return Objects.requireNonNull(addr.getAddress()).getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private void runFullAudit(CommandSender sender) {
        try {
            List<VoteDao.VoteRow> rows = dao.fetchAllForAudit(plugin.getRoundId());

            java.util.Map<String, java.util.List<VoteDao.VoteRow>> byIp = new java.util.LinkedHashMap<>();
            for (VoteDao.VoteRow r : rows) {
                byIp.computeIfAbsent(r.ip, k -> new java.util.ArrayList<>()).add(r);
            }

            List<String> out = new ArrayList<>();
            for (var entry : byIp.entrySet()) {
                String ip = entry.getKey();
                List<VoteDao.VoteRow> group = entry.getValue();

                List<String> bypassers = new ArrayList<>();
                List<String> others = new ArrayList<>();

                for (VoteDao.VoteRow r : group) {
                    String name = Bukkit.getOfflinePlayer(r.uuid).getName();
                    String label = (name != null ? name : r.uuid.toString());
                    if (r.bypass) bypassers.add(label);
                    else others.add(label);
                }

                if (!bypassers.isEmpty()) {
                    out.add(ChatColor.GOLD + "IP: " + ip);
                    out.add(ChatColor.YELLOW + "  With bypass (" + bypassers.size() + "): " + String.join(", ", bypassers));
                    if (!others.isEmpty()) {
                        out.add(ChatColor.GRAY + "  Others on this IP (" + others.size() + "): " + String.join(", ", others));
                    }
                }
            }

            if (out.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "[ModNVote] No bypass voters found.");
            } else {
                int limit = 800;
                StringBuilder chunk = new StringBuilder();
                for (String line : out) {
                    if (chunk.length() + line.length() + 1 > limit) {
                        sender.sendMessage(chunk.toString());
                        chunk = new StringBuilder();
                    }
                    if (chunk.length() > 0) chunk.append("\n");
                    chunk.append(line);
                }
                if (chunk.length() > 0) sender.sendMessage(chunk.toString());
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "[ModNVote] Full audit failed: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("modnvote.vote")) { out.add("yes"); out.add("no"); }
            if (sender.hasPermission("modnvote.status")) out.add("status");
            if (sender.hasPermission("modnvote.verify")) out.add("verify");
            if (sender.hasPermission("modnvote.admin.reset")) out.add("reset");
            if (sender.hasPermission("modnvote.admin.reload")) out.add("reload");
            if (sender.hasPermission("modnvote.admin.audit")) out.add("audit");
            if (sender.hasPermission("modnvote.admin.fullaudit")) out.add("fullaudit");
        }
        return out;
    }
}
