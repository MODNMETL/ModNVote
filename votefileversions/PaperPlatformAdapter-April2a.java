package com.modnmetl.modnvote.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Paper-oriented implementation of the platform adapter.
 *
 * For now, player-bound tasks are routed through the standard Bukkit scheduler.
 * When Folia support is introduced, a dedicated adapter can replace the player-
 * execution strategy without disturbing the domain layer.
 */
public final class PaperPlatformAdapter implements PlatformAdapter {

    private final Plugin plugin;

    public PaperPlatformAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
    // Paper-safe for now.
    // IMPORTANT: This must be replaced with entity/region scheduler logic
    // when implementing Folia support, otherwise cross-thread access issues
    // will occur.
        Bukkit.getScheduler().runTask(plugin, task);
    }
}