package com.modnmetl.modnvote.ui.feedback;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

/**
 * Plays optional, config-driven UI sounds for vote interactions.
 *
 * Design notes:
 * - fully optional
 * - safe to leave disabled
 * - safe to misconfigure (invalid sound names simply no-op)
 * - reads from config at play time so reloads take effect immediately
 */
public final class VoteSoundService {

    private final JavaPlugin plugin;

    public VoteSoundService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void playSelectionAssigned(Player player) {
        play(player, "ui.sounds.selection_assigned");
    }

    public void playSelectionRemoved(Player player) {
        play(player, "ui.sounds.selection_removed");
    }

    public void playReset(Player player) {
        play(player, "ui.sounds.reset");
    }

    public void playReviewAdvance(Player player) {
        play(player, "ui.sounds.review_advance");
    }

    public void playReturnToSelection(Player player) {
        play(player, "ui.sounds.return_to_selection");
    }

    public void playSubmitSuccess(Player player) {
        play(player, "ui.sounds.submit_success");
    }

    public void playSubmitFailure(Player player) {
        play(player, "ui.sounds.submit_failure");
    }

    private void play(Player player, String basePath) {
        Objects.requireNonNull(player, "player");

        if (!plugin.getConfig().getBoolean("ui.sounds.enabled", false)) {
            return;
        }

        String soundName = plugin.getConfig().getString(basePath + ".sound", "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return;
        }

        float volume = (float) plugin.getConfig().getDouble(basePath + ".volume", 1.0D);
        float pitch = (float) plugin.getConfig().getDouble(basePath + ".pitch", 1.0D);

        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}