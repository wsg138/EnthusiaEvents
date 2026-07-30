package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class AdminCommandSupport {

    private AdminCommandSupport() {
    }

    static List<String> filter(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    static Location mapTeleportLocation(EventMap map) {
        Location location = !map.spawns().isEmpty()
                ? map.spawns().values().iterator().next()
                : map.spectatorSpawn();
        if (location != null && location.getWorld() != null) {
            return location;
        }
        World world = map.worldName() == null || map.worldName().isBlank()
                ? null
                : Bukkit.createWorld(new WorldCreator(map.worldName()));
        if (world == null && map.region() != null) {
            world = Bukkit.createWorld(new WorldCreator(map.region().worldName()));
        }
        if (world == null) {
            return null;
        }
        if (location != null) {
            return new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }
        if (map.region() != null) {
            return new Location(
                    world,
                    (map.region().minX() + map.region().maxX()) / 2.0D,
                    map.region().maxY() + 2.0D,
                    (map.region().minZ() + map.region().maxZ()) / 2.0D
            );
        }
        return null;
    }

    static EventType parseEvent(EnthusiaEventsPlugin plugin, CommandSender sender, String raw) {
        try {
            return EventType.parse(raw);
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "invalid-event", Map.of("event", raw));
            return null;
        }
    }

    static EventType parseEventSilently(String raw) {
        try {
            return EventType.parse(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static int parsePositiveInt(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
