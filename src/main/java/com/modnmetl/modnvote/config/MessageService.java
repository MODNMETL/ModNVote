package com.modnmetl.modnvote.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    public List<String> getList(String path) {
        String prefix = messagesConfig.getString("prefix", "&6[ModNVote] ");
        List<String> raw = messagesConfig.getStringList(path);

        if (raw.isEmpty()) {
            return List.of(color(prefix + "&cMissing message list: " + path));
        }

        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(color(prefix + line));
        }
        return List.copyOf(out);
    }

    public List<String> getRawList(String path) {
        List<String> raw = messagesConfig.getStringList(path);

        if (raw.isEmpty()) {
            return List.of(color("&cMissing message list: " + path));
        }

        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(color(line));
        }
        return List.copyOf(out);
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

    public List<String> formatList(String path, Map<String, String> placeholders) {
        List<String> lines = getList(path);
        List<String> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            String formatted = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            out.add(formatted);
        }

        return List.copyOf(out);
    }

    public List<String> formatRawList(String path, Map<String, String> placeholders) {
        List<String> lines = getRawList(path);
        List<String> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            String formatted = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            out.add(formatted);
        }

        return List.copyOf(out);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}