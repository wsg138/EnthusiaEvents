package org.enthusia.events.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventType;
import org.enthusia.events.stats.EventStatsService;
import org.enthusia.events.stats.PlayerEventStats;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EventsPlaceholderExpansion extends PlaceholderExpansion {

    private final EnthusiaEventsPlugin plugin;
    private final EventStatsService statsService;

    public EventsPlaceholderExpansion(EnthusiaEventsPlugin plugin, EventStatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @Override
    public String getIdentifier() {
        return "ee";
    }

    @Override
    public String getAuthor() {
        return "P2wn";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        PlayerEventStats stats = statsService.stats(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "events_played" -> String.valueOf(stats.eventsPlayed());
            case "events_wins" -> String.valueOf(stats.wins());
            case "events_losses" -> String.valueOf(stats.losses());
            default -> resolveComplex(params, stats);
        };
    }

    private String resolveComplex(String params, PlayerEventStats stats) {
        if (params.startsWith("top_wins_")) {
            int index = Integer.parseInt(params.substring("top_wins_".length())) - 1;
            List<Map.Entry<UUID, PlayerEventStats>> leaders = statsService.topWins(10);
            if (index < 0 || index >= leaders.size()) {
                return "";
            }
            return plugin.getServer().getOfflinePlayer(leaders.get(index).getKey()).getName();
        }
        String[] parts = params.split("_");
        if (parts.length < 2) {
            return "";
        }
        String suffix = parts[parts.length - 1];
        String eventName = params.substring(0, params.length() - suffix.length() - 1).toUpperCase(Locale.ROOT);
        EventType type;
        try {
            type = EventType.parse(eventName);
        } catch (IllegalArgumentException ex) {
            return "";
        }
        return switch (suffix) {
            case "wins" -> String.valueOf(stats.winsByEvent().getOrDefault(type, 0));
            case "played" -> String.valueOf(stats.playedByEvent().getOrDefault(type, 0));
            case "losses" -> String.valueOf(stats.lossesByEvent().getOrDefault(type, 0));
            default -> "";
        };
    }
}
