package com.modnmetl.modnvote.platform;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Platform abstraction layer used to isolate Bukkit/Paper/Folia-sensitive work.
 *
 * The immediate implementation targets Paper. Later, a Folia-specific adapter can
 * implement the same contract without forcing domain and service logic to change.
 */
public interface PlatformAdapter {

    Plugin plugin();

    void runSync(Runnable task);

    void runAsync(Runnable task);

    void runForPlayer(Player player, Runnable task);
}