package com.modnmetl.modnvote.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Objects;

/**
 * Loads and formats all user/admin-facing ModNVote messages from messages.yml.
 *
 * This keeps messaging configurable for open-source server owners and makes it
 * easier to provide clearer, more educational wording for players.
 */
public final class MessageService {

    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;

    public MessageService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String prefix = messagesConfig.getString("prefix", "&6[ModNVote] ");
        String raw = messagesConfig.getString(path, "&cMissing message: " + path);
        return color(prefix + raw);
    }

    public String getRaw(String path) {
        String raw = messagesConfig.getString(path, "&cMissing message: " + path);
        return color(raw);
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public String formatRaw(String path, Map<String, String> placeholders) {
        String message = getRaw(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}