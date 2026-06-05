package org.enthusia.events.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Messages {

    private final Plugin plugin;
    private File file;
    private FileConfiguration config;

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource("messages.yml");
        if (stream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            upgradeManagedMessages(defaults);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save messages.yml defaults: " + e.getMessage());
            }
        }
    }

    public String get(String key) {
        String raw = config.getString(key, "");
        return ChatColor.translateAlternateColorCodes('&', raw.replace("{prefix}", config.getString("prefix", "")));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public String format(String key, Map<String, String> placeholders) {
        String value = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(format(key, placeholders));
    }

    private void upgradeManagedMessages(YamlConfiguration defaults) {
        replaceIfOld(defaults, "manual-event-started-by",
                "{prefix}&e{event} has been started by &f{player}&e. Run &f/event join&e.");
        replaceIfOld(defaults, "manual-event-started-by",
                "{prefix}&6{event}&7 was started by &f{player}&7.");
        replaceIfOld(defaults, "join-countdown-started",
                "{prefix}&e{event} join phase started. Run &f/event join&e. Starts in &f{seconds}&e seconds, minimum players: &f{min}&e.");
        replaceIfOld(defaults, "join-countdown-started",
                "{prefix}&6{event}&7 is open. &f{players}&7 players joined. Starts in &f{seconds}s&7.");
        replaceIfOld(defaults, "vote-started",
                "{prefix}&6Event vote started. &7Use &f/event vote&7 to choose what plays next.");
        replaceIfOld(defaults, "vote-starting-soon",
                "{prefix}&6An event is starting soon. &7Use &f/event vote&7 to pick the event.");
        replaceIfOld(defaults, "countdown-tick",
                "{prefix}&e{event} starting in &f{seconds}&e seconds. Players: &f{players}/{min}&e.");
        replaceIfOld(defaults, "countdown-tick",
                "{prefix}&6{event}&7 starts in &f{seconds}s&8 | &7Players: &f{players}/{min}");
        replaceIfOld(defaults, "force-started",
                "{prefix}&aStarted &f{event}&a. Players can now run &f/event join&a.");
        replaceIfOld(defaults, "event-active-started",
                "{prefix}&a{event} has started.");
        replaceIfOld(defaults, "event-ended",
                "{prefix}&e{event} ended. Winner(s): &f{winners}&e.");
        replaceIfOld(defaults, "event-ended-no-winner",
                "{prefix}&e{event} ended with no winner.");
        replaceIfOld(defaults, "event-winner-paid",
                "{prefix}&aYou won &f${amount}&a from the event.");
        replaceIfOld(defaults, "config-reset-done",
                "{prefix}&aReset config.yml and messages.yml defaults. Map setup data was kept.");
    }

    private void replaceIfOld(YamlConfiguration defaults, String key, String oldValue) {
        String current = config.getString(key, "");
        String updated = defaults.getString(key, current);
        if (current.equals(oldValue)) {
            config.set(key, updated);
        }
    }
}
