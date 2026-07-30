package org.enthusia.events.command;

import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapSetupService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AdminSetupTabCompleter {

    private static final List<String> SETUP_TOOLS = List.of(
            "save", "pos1", "pos2", "spawn", "spectator", "checkpoint", "checkpoint_spawn", "finish",
            "chest", "generator", "point", "area_pos1", "area_pos2", "area"
    );

    private final MapSetupService mapSetupService;

    AdminSetupTabCompleter(MapSetupService mapSetupService) {
        this.mapSetupService = mapSetupService;
    }

    List<String> complete(String[] args) {
        return switch (args.length) {
            case 2 -> completeRoot(args[1]);
            case 3 -> completeMapId(args);
            case 4 -> AdminCommandSupport.filter(args[3], SETUP_TOOLS);
            case 5 -> completeToolValue(args);
            default -> List.of();
        };
    }

    private List<String> completeRoot(String input) {
        List<String> values = new ArrayList<>();
        values.add("cancel");
        values.add("save");
        values.addAll(eventTypes());
        return AdminCommandSupport.filter(input, values);
    }

    private List<String> completeMapId(String[] args) {
        EventType type = AdminCommandSupport.parseEventSilently(args[1]);
        return type == null ? List.of() : AdminCommandSupport.filter(args[2], mapIds(type));
    }

    private List<String> completeToolValue(String[] args) {
        EventType type = AdminCommandSupport.parseEventSilently(args[1]);
        EventMap map = type == null ? null : mapSetupService.find(type, args[2]).orElse(null);
        return AdminCommandSupport.filter(args[4], setupValues(type, map, args[3]));
    }

    private List<String> setupValues(EventType type, EventMap map, String rawTool) {
        if (type == null) {
            return List.of();
        }
        return switch (rawTool.toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawnIds(map);
            case "checkpoint" -> checkpointIds(map);
            case "checkpoint_spawn" -> checkpointSpawnIds(map);
            case "finish" -> List.of("finish-1", "finish-2");
            case "chest" -> List.of("1", "2", "3");
            case "generator" -> generatorIds(type, map);
            case "point" -> pointIds(type, map);
            case "area" -> areaIds(type, map);
            default -> List.of();
        };
    }

    private List<String> eventTypes() {
        return List.of(EventType.values()).stream().map(Enum::name).toList();
    }

    private List<String> mapIds(EventType type) {
        return mapSetupService.mapsFor(type).stream().map(EventMap::id).toList();
    }

    private List<String> checkpointSpawnIds(EventMap map) {
        if (map == null) {
            return List.of("checkpoint-spawn-1");
        }
        return map.checkpoints().keySet().stream()
                .filter(key -> !key.startsWith("finish"))
                .map(key -> "checkpoint-spawn-" + key)
                .toList();
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

    private List<String> generatorIds(EventType type, EventMap map) {
        List<String> ids = new ArrayList<>();
        if (map != null) {
            ids.addAll(map.generators().keySet());
        }
        if (type == EventType.BEDWARS) {
            ids.addAll(List.of("team-generator", "diamond", "emerald"));
        }
        return ids.stream().distinct().toList();
    }

    private List<String> pointIds(EventType type, EventMap map) {
        List<String> ids = new ArrayList<>();
        if (map != null) {
            ids.addAll(map.points().keySet());
        }
        if (type == EventType.BEDWARS) {
            ids.addAll(List.of("team-bed", "team-item-shop", "team-upgrade-shop", "team-heal-pool"));
        } else if (type == EventType.CAPTURE_THE_FLAG) {
            ids.add("team-flag");
        }
        return ids.stream().distinct().toList();
    }

    private List<String> areaIds(EventType type, EventMap map) {
        List<String> ids = new ArrayList<>();
        if (map != null) {
            ids.addAll(map.areas().keySet());
        }
        if (type == EventType.CAPTURE_PLAYERS) {
            ids.addAll(List.of("capture-zone", "jail-zone", "free-zone"));
        } else if (type == EventType.BLOCK_PARTY) {
            ids.add("color-floor");
        } else if (type == EventType.PARKOUR || type == EventType.BOAT_RACE || type == EventType.HORSE_RACE) {
            ids.add("release-wall");
        } else if (type == EventType.RED_LIGHT_GREEN_LIGHT) {
            ids.addAll(List.of("light-display", "finish-line"));
        }
        return ids.stream().distinct().toList();
    }
}
