package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AdminMapTabCompleter {

    private static final List<String> MAP_COMMANDS = List.of(
            "create", "clear", "pos1", "pos2", "region", "spawn", "spectator", "checkpoint", "list",
            "tp", "retarget", "status", "transfer", "transferall", "export", "exporthub", "exporttrophy",
            "exportglobal", "exportall"
    );
    private static final List<String> EVENT_SUBCOMMANDS = List.of(
            "create", "clear", "region", "spawn", "spectator", "checkpoint", "list", "tp", "teleport",
            "retarget", "transfer", "export"
    );
    private static final List<String> MAP_ID_SUBCOMMANDS = List.of(
            "clear", "region", "spawn", "spectator", "checkpoint", "tp", "teleport", "retarget",
            "transfer", "export"
    );
    private static final List<String> CLEAR_SECTIONS = List.of(
            "all", "region", "spectator", "spawns", "finishes", "checkpoints", "checkpoint-blocks",
            "points", "beds", "shops", "areas", "chests", "generators"
    );

    private final MapSetupService mapSetupService;
    private final MapCopyService mapCopyService;

    AdminMapTabCompleter(MapSetupService mapSetupService, MapCopyService mapCopyService) {
        this.mapSetupService = mapSetupService;
        this.mapCopyService = mapCopyService;
    }

    List<String> complete(String[] args) {
        return switch (args.length) {
            case 2 -> AdminCommandSupport.filter(args[1], MAP_COMMANDS);
            case 3 -> completeEventType(args);
            case 4 -> completeMapId(args);
            case 5 -> completeMapValue(args);
            case 6 -> completeConfirmation(args);
            default -> List.of();
        };
    }

    private List<String> completeEventType(String[] args) {
        if (!EVENT_SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        return AdminCommandSupport.filter(args[2], eventTypes());
    }

    private List<String> completeMapId(String[] args) {
        if (!MAP_ID_SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        EventType type = AdminCommandSupport.parseEventSilently(args[2]);
        return type == null ? List.of() : AdminCommandSupport.filter(args[3], mapIds(type));
    }

    private List<String> completeMapValue(String[] args) {
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (subcommand.equals("clear")) {
            return AdminCommandSupport.filter(args[4], CLEAR_SECTIONS);
        }
        if (subcommand.equals("retarget")) {
            return AdminCommandSupport.filter(args[4], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        EventMap map = selectedMap(args);
        return switch (subcommand) {
            case "spawn" -> AdminCommandSupport.filter(args[4], spawnIds(map));
            case "checkpoint" -> AdminCommandSupport.filter(args[4], checkpointIds(map));
            case "transfer" -> AdminCommandSupport.filter(args[4], List.of("confirm"));
            case "export" -> map == null
                    ? List.of()
                    : AdminCommandSupport.filter(args[4], List.of(mapCopyService.defaultWorldName(map)));
            default -> List.of();
        };
    }

    private List<String> completeConfirmation(String[] args) {
        if (args[1].equalsIgnoreCase("clear") && args[4].equalsIgnoreCase("all")) {
            return AdminCommandSupport.filter(args[5], List.of("confirm"));
        }
        if (args[1].equalsIgnoreCase("transfer")) {
            return AdminCommandSupport.filter(args[5], List.of("confirm"));
        }
        return List.of();
    }

    private EventMap selectedMap(String[] args) {
        EventType type = AdminCommandSupport.parseEventSilently(args[2]);
        return type == null ? null : mapSetupService.find(type, args[3]).orElse(null);
    }

    private List<String> eventTypes() {
        return List.of(EventType.values()).stream().map(Enum::name).toList();
    }

    private List<String> mapIds(EventType type) {
        return mapSetupService.mapsFor(type).stream().map(EventMap::id).toList();
    }

    private List<String> spawnIds(EventMap map) {
        List<String> ids = new ArrayList<>();
        if (map != null) {
            ids.addAll(map.spawns().keySet());
        }
        ids.addAll(List.of("start", "team-1-spawn", "team-2-spawn"));
        return ids.stream().distinct().toList();
    }

    private List<String> checkpointIds(EventMap map) {
        List<String> ids = new ArrayList<>();
        if (map != null) {
            ids.addAll(map.checkpoints().keySet());
        }
        ids.addAll(List.of("checkpoint-1", "checkpoint-2", "finish-1"));
        return ids.stream().distinct().toList();
    }
}
