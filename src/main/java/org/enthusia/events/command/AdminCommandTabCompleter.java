package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.kit.EventKitService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AdminCommandTabCompleter {

    private static final List<String> ROOT_COMMANDS = List.of(
            "autostart", "disable", "enable", "enabled", "disabled", "status", "private", "invite",
            "forcestart", "simulatevote", "advance", "forcestop", "eventtp", "stop", "restore",
            "stuckcheck", "emergencyrestore", "remove", "reload", "retryrestores", "kit",
            "resetconfigs", "resetloot", "map", "setup"
    );
    private static final List<String> MAP_COMMANDS = List.of(
            "create", "clear", "pos1", "pos2", "region", "spawn", "spectator", "checkpoint", "list",
            "tp", "retarget", "status", "transfer", "transferall", "export", "exporthub", "exporttrophy",
            "exportglobal", "exportall"
    );
    private static final List<String> SETUP_TOOLS = List.of(
            "save", "pos1", "pos2", "spawn", "spectator", "checkpoint", "checkpoint_spawn", "finish",
            "chest", "generator", "point", "area_pos1", "area_pos2", "area"
    );

    private final MapSetupService mapSetupService;
    private final MapCopyService mapCopyService;
    private final EventKitService kitService;

    AdminCommandTabCompleter(MapSetupService mapSetupService, MapCopyService mapCopyService,
                             EventKitService kitService) {
        this.mapSetupService = mapSetupService;
        this.mapCopyService = mapCopyService;
        this.kitService = kitService;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("private")) {
            return onlinePlayers(args[args.length - 1]);
        }
        return switch (args.length) {
            case 1 -> AdminCommandSupport.filter(args[0], ROOT_COMMANDS);
            case 2 -> completeSecondArgument(args);
            case 3 -> completeThirdArgument(args);
            case 4 -> completeFourthArgument(args);
            case 5 -> completeFifthArgument(args);
            case 6 -> completeSixthArgument(args);
            default -> List.of();
        };
    }

    private List<String> completeSecondArgument(String[] args) {
        String command = args[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "disable", "enable", "private", "forcestart", "eventtp", "eventteleport" ->
                    AdminCommandSupport.filter(args[1], eventTypes());
            case "invite", "restore", "stuckcheck", "emergencyrestore", "remove" -> onlinePlayers(args[1]);
            case "autostart" -> AdminCommandSupport.filter(args[1], List.of("on", "off", "status"));
            case "kit" -> AdminCommandSupport.filter(args[1], List.of("save", "list", "select", "override"));
            case "setup" -> completeSetupRoot(args[1]);
            case "map" -> AdminCommandSupport.filter(args[1], MAP_COMMANDS);
            default -> List.of();
        };
    }

    private List<String> completeThirdArgument(String[] args) {
        String command = args[0].toLowerCase(Locale.ROOT);
        if (command.equals("kit") && (args[1].equalsIgnoreCase("select") || args[1].equalsIgnoreCase("override"))) {
            return AdminCommandSupport.filter(args[2], kitNames());
        }
        if (command.equals("eventtp") || command.equals("eventteleport")) {
            EventType type = AdminCommandSupport.parseEventSilently(args[1]);
            return type == null ? List.of() : AdminCommandSupport.filter(args[2], mapIds(type));
        }
        if (command.equals("setup")) {
            EventType type = AdminCommandSupport.parseEventSilently(args[1]);
            return type == null ? List.of() : AdminCommandSupport.filter(args[2], mapIds(type));
        }
        if (command.equals("map") && mapSubcommandNeedsEvent(args[1])) {
            return AdminCommandSupport.filter(args[2], eventTypes());
        }
        return List.of();
    }

    private List<String> completeFourthArgument(String[] args) {
        if (args[0].equalsIgnoreCase("kit") && args[1].equalsIgnoreCase("override")) {
            return AdminCommandSupport.filter(args[3], kitNames());
        }
        if (args[0].equalsIgnoreCase("setup")) {
            return AdminCommandSupport.filter(args[3], SETUP_TOOLS);
        }
        if (args[0].equalsIgnoreCase("map") && mapSubcommandNeedsMapId(args[1])) {
            EventType type = AdminCommandSupport.parseEventSilently(args[2]);
            return type == null ? List.of() : AdminCommandSupport.filter(args[3], mapIds(type));
        }
        return List.of();
    }

    private List<String> completeFifthArgument(String[] args) {
        if (args[0].equalsIgnoreCase("setup")) {
            EventType type = AdminCommandSupport.parseEventSilently(args[1]);
            EventMap map = type == null ? null : mapSetupService.find(type, args[2]).orElse(null);
            return AdminCommandSupport.filter(args[4], setupValues(type, map, args[3]));
        }
        if (!args[0].equalsIgnoreCase("map")) {
            return List.of();
        }
        if (args[1].equalsIgnoreCase("clear")) {
            return AdminCommandSupport.filter(args[4], List.of(
                    "all", "region", "spectator", "spawns", "finishes", "checkpoints", "checkpoint-blocks",
                    "points", "beds", "shops", "areas", "chests", "generators"
            ));
        }
        if (args[1].equalsIgnoreCase("retarget")) {
            return AdminCommandSupport.filter(args[4], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        EventType type = AdminCommandSupport.parseEventSilently(args[2]);
        EventMap map = type == null ? null : mapSetupService.find(type, args[3]).orElse(null);
        if (args[1].equalsIgnoreCase("spawn")) {
            return AdminCommandSupport.filter(args[4], spawnIds(map));
        }
        if (args[1].equalsIgnoreCase("checkpoint")) {
            return AdminCommandSupport.filter(args[4], checkpointIds(map));
        }
        if (args[1].equalsIgnoreCase("transfer")) {
            return AdminCommandSupport.filter(args[4], List.of("confirm"));
        }
        if (args[1].equalsIgnoreCase("export") && map != null) {
            return AdminCommandSupport.filter(args[4], List.of(mapCopyService.defaultWorldName(map)));
        }
        return List.of();
    }

    private List<String> completeSixthArgument(String[] args) {
        if (!args[0].equalsIgnoreCase("map")) {
            return List.of();
        }
        if (args[1].equalsIgnoreCase("clear") && args[4].equalsIgnoreCase("all")) {
            return AdminCommandSupport.filter(args[5], List.of("confirm"));
        }
        if (args[1].equalsIgnoreCase("transfer")) {
            return AdminCommandSupport.filter(args[5], List.of("confirm"));
        }
        return List.of();
    }

    private List<String> completeSetupRoot(String input) {
        List<String> values = new ArrayList<>();
        values.add("cancel");
        values.add("save");
        values.addAll(eventTypes());
        return AdminCommandSupport.filter(input, values);
    }

    private List<String> onlinePlayers(String input) {
        return AdminCommandSupport.filter(input, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    private List<String> eventTypes() {
        return List.of(EventType.values()).stream().map(Enum::name).toList();
    }

    private List<String> kitNames() {
        return kitService.kits().stream().map(EventKitService.EventKit::name).toList();
    }

    private boolean mapSubcommandNeedsEvent(String subcommand) {
        return List.of("create", "clear", "region", "spawn", "spectator", "checkpoint", "list", "tp", "teleport",
                "retarget", "transfer", "export").contains(subcommand.toLowerCase(Locale.ROOT));
    }

    private boolean mapSubcommandNeedsMapId(String subcommand) {
        return List.of("clear", "region", "spawn", "spectator", "checkpoint", "tp", "teleport", "retarget",
                "transfer", "export").contains(subcommand.toLowerCase(Locale.ROOT));
    }

    private List<String> mapIds(EventType type) {
        return mapSetupService.mapsFor(type).stream().map(EventMap::id).toList();
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
