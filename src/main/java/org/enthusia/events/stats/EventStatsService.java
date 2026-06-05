package org.enthusia.events.stats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventType;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventStatsService {

    private final EnthusiaEventsPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerEventStats> stats = new HashMap<>();

    public EventStatsService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public PlayerEventStats stats(UUID uuid) {
        return stats.computeIfAbsent(uuid, ignored -> new PlayerEventStats());
    }

    public void recordParticipation(UUID uuid, EventType type) {
        stats(uuid).recordParticipation(type);
        saveIfConfigured();
    }

    public void recordWin(UUID uuid, EventType type) {
        stats(uuid).recordWin(type);
        saveIfConfigured();
    }

    public void recordLoss(UUID uuid, EventType type) {
        stats(uuid).recordLoss(type);
        saveIfConfigured();
    }

    public List<Map.Entry<UUID, PlayerEventStats>> topWins(int limit) {
        return stats.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> -entry.getValue().wins()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void sendSummary(Player player) {
        PlayerEventStats value = stats(player.getUniqueId());
        plugin.messages().send(player, "stats-header", Map.of(
                "played", String.valueOf(value.eventsPlayed()),
                "wins", String.valueOf(value.wins()),
                "losses", String.valueOf(value.losses())
        ));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerEventStats> entry : stats.entrySet()) {
            String base = "players." + entry.getKey();
            PlayerEventStats value = entry.getValue();
            yaml.set(base + ".events-played", value.eventsPlayed());
            yaml.set(base + ".wins", value.wins());
            yaml.set(base + ".losses", value.losses());
            yaml.set(base + ".win-streak", value.winStreak());
            yaml.set(base + ".best-streak", value.bestStreak());
            for (EventType type : EventType.values()) {
                yaml.set(base + ".per-event." + type.name() + ".played", value.playedByEvent().getOrDefault(type, 0));
                yaml.set(base + ".per-event." + type.name() + ".wins", value.winsByEvent().getOrDefault(type, 0));
                yaml.set(base + ".per-event." + type.name() + ".losses", value.lossesByEvent().getOrDefault(type, 0));
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save stats.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            UUID uuid = UUID.fromString(key);
            PlayerEventStats value = new PlayerEventStats();
            value.loadTotals(
                    section.getInt("events-played"),
                    section.getInt("wins"),
                    section.getInt("losses"),
                    section.getInt("win-streak"),
                    section.getInt("best-streak")
            );
            stats.put(uuid, value);

            ConfigurationSection perEvent = section.getConfigurationSection("per-event");
            if (perEvent == null) {
                continue;
            }
            for (String eventKey : perEvent.getKeys(false)) {
                EventType type = EventType.valueOf(eventKey);
                ConfigurationSection eventSection = perEvent.getConfigurationSection(eventKey);
                if (eventSection == null) {
                    continue;
                }
                value.loadPerEvent(
                        type,
                        eventSection.getInt("played"),
                        eventSection.getInt("wins"),
                        eventSection.getInt("losses")
                );
            }
        }
    }

    private void saveIfConfigured() {
        if (plugin.getConfig().getBoolean("stats.save-on-change", true)) {
            save();
        }
    }
}
