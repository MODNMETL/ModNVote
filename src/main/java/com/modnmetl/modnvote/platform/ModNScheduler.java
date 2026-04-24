package com.modnmetl.modnvote.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Small scheduler bridge used to keep player-affecting work compatible with
 * both Paper's classic Bukkit scheduler and Folia's entity/async schedulers.
 *
 * This class intentionally uses reflection for Folia-specific calls so the
 * plugin can still compile against the normal Paper API used by the current
 * Gradle build.
 */
public final class ModNScheduler {

    private final Plugin plugin;
    private final Logger logger;
    private final boolean foliaRuntime;

    public ModNScheduler(Plugin plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.foliaRuntime = isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");
    }

    public boolean isFoliaRuntime() {
        return foliaRuntime;
    }

    public void runForPlayer(Player player, Runnable task) {
        runForPlayerLater(player, task, 1L);
    }

    public void runForPlayerLater(Player player, Runnable task, long delayTicks) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");

        if (foliaRuntime) {
            runFoliaEntityLater(player, task, Math.max(1L, delayTicks));
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    public void runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");

        if (foliaRuntime) {
            runFoliaAsync(task);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @SuppressWarnings("unchecked")
    private void runFoliaEntityLater(Player player, Runnable task, long delayTicks) {
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(player);
            Method runDelayed = entityScheduler.getClass().getMethod(
                    "runDelayed",
                    Plugin.class,
                    Consumer.class,
                    Runnable.class,
                    long.class
            );

            Consumer<Object> scheduledTaskConsumer = ignored -> {
                if (player.isOnline()) {
                    task.run();
                }
            };

            runDelayed.invoke(entityScheduler, plugin, scheduledTaskConsumer, null, delayTicks);
        } catch (ReflectiveOperationException e) {
            logger.severe("Failed to schedule Folia entity task for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void runFoliaAsync(Runnable task) {
        try {
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            Object asyncScheduler = getAsyncScheduler.invoke(null);
            Method runNow = asyncScheduler.getClass().getMethod(
                    "runNow",
                    Plugin.class,
                    Consumer.class
            );

            Consumer<Object> scheduledTaskConsumer = ignored -> task.run();
            runNow.invoke(asyncScheduler, plugin, scheduledTaskConsumer);
        } catch (ReflectiveOperationException e) {
            logger.severe("Failed to schedule Folia async task: " + e.getMessage());
        }
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
