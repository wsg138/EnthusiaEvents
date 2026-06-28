package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.CuboidRegion;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.gui.RestoreConfirmGui;
import org.enthusia.events.kit.EventKitService;
import org.enthusia.events.setup.SetupSession;
import org.enthusia.events.setup.SetupTool;
import org.enthusia.events.setup.SetupWizard;
import org.enthusia.events.util.LocationCodec;
import org.enthusia.events.util.TeleportService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings({
        "PMD.UseConcurrentHashMap",
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.NPathComplexity"
})
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final MapSetupService mapSetupService;
    private final SetupWizard setupWizard;
    private final MapCopyService mapCopyService;
    private final RestoreConfirmGui restoreConfirmGui;
    private final EventKitService kitService;
    private final Map<java.util.UUID, Location> pos1Selections = new HashMap<>();
    private final Map<java.util.UUID, Location> pos2Selections = new HashMap<>();

    public AdminCommand(EnthusiaEventsPlugin plugin, EventManager eventManager, MapSetupService mapSetupService,
                        SetupWizard setupWizard, MapCopyService mapCopyService, RestoreConfirmGui restoreConfirmGui,
                        EventKitService kitService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.mapSetupService = mapSetupService;
        this.setupWizard = setupWizard;
        this.mapCopyService = mapCopyService;
        this.restoreConfirmGui = restoreConfirmGui;
        this.kitService = kitService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enthusia.events.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "forcestart", "simulatevote" -> {
                boolean started;
                if (args[0].equalsIgnoreCase("simulatevote")) {
                    started = eventManager.startScheduledVote();
                } else if (args.length >= 2) {
                    EventType type = parseEvent(sender, args[1]);
                    if (type == null) {
                        return true;
                    }
                    started = eventManager.startForcedEvent(type);
                } else {
                    started = eventManager.startScheduledVote();
                    if (started) {
                        plugin.messages().send(sender, "force-started", Map.of("event", "random vote"));
                    }
                }
                if (!started) {
                    String reason = args.length >= 2
                            ? eventManager.forcedStartFailureReason(EventType.valueOf(args[1].toUpperCase(Locale.ROOT)))
                            : eventManager.manualStartFailureReason(sender instanceof Player player ? player : null, null, false);
                    plugin.messages().send(sender, "force-start-failed", Map.of("reason", reason));
                }
            }
            case "advance" -> {
                if (eventManager.advancePhase()) {
                    String phase = eventManager.session() == null ? "ended" : eventManager.session().phase().name();
                    plugin.messages().send(sender, "event-advanced", Map.of("phase", phase));
                } else {
                    plugin.messages().send(sender, "event-advance-failed");
                }
            }
            case "forcestop" -> {
                eventManager.stop("admin-force");
                plugin.messages().send(sender, "force-stopped");
            }
            case "quicktest" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee quicktest <PARKOUR|SPLEEF>");
                    return true;
                }
                EventType type = parseEvent(sender, args[1]);
                if (type == null) {
                    return true;
                }
                if (eventManager.startQuickTest(player, type)) {
                    plugin.messages().send(sender, "quick-test-started", Map.of("event", type.name()));
                } else {
                    plugin.messages().send(sender, "quick-test-failed", Map.of("event", type.name()));
                }
            }
            case "stop" -> {
                eventManager.stop("admin");
                plugin.messages().send(sender, "admin-stop");
            }
            case "restore" -> {
                if (args.length < 2) {
                    return false;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.messages().send(sender, "event-no-snapshot");
                    return true;
                }
                if (sender instanceof Player admin) {
                    restoreConfirmGui.open(admin, target);
                } else if (eventManager.restoreSnapshot(target)) {
                    plugin.messages().send(sender, "event-restored");
                } else {
                    plugin.messages().send(sender, "event-no-snapshot");
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    return false;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    eventManager.leave(target);
                }
            }
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.messages().send(sender, "reload-done");
            }
            case "retryrestores" -> {
                int count = eventManager.retryPendingOnlineRestores();
                plugin.messages().send(sender, "event-restore-retry-started", Map.of("count", String.valueOf(count)));
            }
            case "kit" -> {
                return handleKitCommand(sender, args);
            }
            case "resetconfigs" -> {
                plugin.resetGeneratedConfigs();
                plugin.messages().send(sender, "config-reset-done");
            }
            case "resetloot" -> {
                plugin.resetLootConfig();
                plugin.messages().send(sender, "loot-reset-done");
            }
            case "sethub" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "player-only");
                    return true;
                }
                plugin.getConfig().set("locations.waiting-hub", LocationCodec.encode(player.getLocation()));
                plugin.saveConfig();
                plugin.messages().send(sender, "setup-saved", Map.of("target", "waiting hub"));
            }
            case "settrophy" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "player-only");
                    return true;
                }
                plugin.getConfig().set("locations.trophy-room", LocationCodec.encode(player.getLocation()));
                plugin.saveConfig();
                plugin.messages().send(sender, "setup-saved", Map.of("target", "trophy room"));
            }
            case "quicksetup" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee quicksetup <PARKOUR|SPLEEF> <mapId> [radius]");
                    return true;
                }
                EventType type = parseEvent(sender, args[1]);
                if (type == null) {
                    return true;
                }
                int radius;
                try {
                    radius = args.length >= 4 ? Integer.parseInt(args[3]) : 96;
                } catch (NumberFormatException ex) {
                    plugin.messages().send(sender, "invalid-number", Map.of("value", args[3]));
                    return true;
                }
                mapSetupService.quickSetup(type, args[2], player.getLocation(), radius);
                if (plugin.getConfig().getString("locations.waiting-hub", "").isBlank()) {
                    plugin.getConfig().set("locations.waiting-hub", LocationCodec.encode(player.getLocation()));
                }
                if (plugin.getConfig().getString("locations.trophy-room", "").isBlank()) {
                    plugin.getConfig().set("locations.trophy-room", LocationCodec.encode(player.getLocation()));
                }
                plugin.saveConfig();
                plugin.messages().send(sender, "quick-setup-done", Map.of("event", type.name(), "map", args[2]));
            }
            case "setup" -> {
                return handleSetupCommand(sender, args);
            }
            case "map" -> {
                return handleMapCommand(sender, args);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("forcestart", "simulatevote", "advance", "forcestop", "quicktest", "stop", "restore", "remove", "reload", "retryrestores", "kit", "resetconfigs", "resetloot", "sethub", "settrophy", "map", "setup", "quicksetup"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kit")) {
            return filter(args[1], List.of("save", "list", "select", "override"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("kit") && (args[1].equalsIgnoreCase("select") || args[1].equalsIgnoreCase("override"))) {
            return filter(args[2], kitService.kits().stream().map(EventKitService.EventKit::name).toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("kit") && args[1].equalsIgnoreCase("override")) {
            return filter(args[3], kitService.kits().stream().map(EventKitService.EventKit::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("forcestart")) {
            return filter(args[1], List.of(EventType.values()).stream().map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quicktest")) {
            return filter(args[1], List.of(EventType.PARKOUR, EventType.SPLEEF).stream().map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quicksetup")) {
            return filter(args[1], List.of(EventType.PARKOUR, EventType.SPLEEF).stream().map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            List<String> values = new ArrayList<>();
            values.add("cancel");
            values.addAll(List.of(EventType.values()).stream().map(Enum::name).toList());
            return filter(args[1], values);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup")) {
            return filter(args[3], List.of("pos1", "pos2", "spawn", "spectator", "checkpoint", "checkpoint_spawn", "finish", "chest", "generator", "point", "area_pos1", "area_pos2", "area"));
        }
        if (args.length == 2 && List.of("restore", "remove").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("map")) {
            return filter(args[1], List.of("create", "pos1", "pos2", "region", "spawn", "spectator", "checkpoint", "list", "tp", "status", "transfer", "transferall", "export", "exporthub", "exporttrophy", "exportglobal", "exportall"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("map")) {
            return filter(args[2], List.of(EventType.values()).stream().map(Enum::name).toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("map")
                && (args[1].equalsIgnoreCase("transfer") || args[1].equalsIgnoreCase("export"))) {
            EventType type = parseEventSilently(args[2]);
            if (type != null) {
                return filter(args[3], mapSetupService.mapsFor(type).stream().map(EventMap::id).toList());
            }
        }
        return List.of();
    }

    private boolean handleSetupCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            if (setupWizard.cancel(player)) {
                plugin.messages().send(sender, "setup-cancelled");
            } else {
                plugin.messages().send(sender, "setup-failed", Map.of("reason", "no setup mode was active"));
            }
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("/ee setup <EVENT> <mapId> <pos1|pos2|spawn|spectator|checkpoint|checkpoint_spawn|finish|chest|generator|point|area_pos1|area_pos2|area> [name|tier|type]");
            return true;
        }
        EventType eventType = parseEvent(sender, args[1]);
        if (eventType == null) {
            return true;
        }
        if (mapSetupService.find(eventType, args[2]).isEmpty()) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found; run /ee map create first"));
            return true;
        }
        SetupTool tool;
        try {
            tool = SetupTool.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "unknown setup mode"));
            return true;
        }
        String value = args.length >= 5 ? args[4] : "";
        if ((tool == SetupTool.SPAWN || tool == SetupTool.CHECKPOINT || tool == SetupTool.CHECKPOINT_SPAWN || tool == SetupTool.CHEST || tool == SetupTool.GENERATOR
                || tool == SetupTool.POINT || tool == SetupTool.AREA)
                && value.isBlank()) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "that setup mode needs a name, tier, or type"));
            return true;
        }
        if (tool == SetupTool.CHEST) {
            try {
                int tier = Integer.parseInt(value);
                if (tier < 1 || tier > 3) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "chest tier must be 1, 2, or 3"));
                    return true;
                }
            } catch (NumberFormatException ex) {
                plugin.messages().send(sender, "invalid-number", Map.of("value", value));
                return true;
            }
        }
        setupWizard.begin(player, new SetupSession(eventType, args[2], tool, value));
        return true;
    }

    private boolean handleMapCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player player) || args.length < 4) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee map create <EVENT> <mapId>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                EventMap map = mapSetupService.create(type, args[3], player.getWorld().getName());
                plugin.messages().send(sender, "map-created", Map.of("map", map.id(), "event", type.name()));
                return true;
            }
            case "pos1" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "player-only");
                    return true;
                }
                pos1Selections.put(player.getUniqueId(), player.getLocation());
                plugin.messages().send(sender, "setup-saved", Map.of("target", "pos1"));
                return true;
            }
            case "pos2" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "player-only");
                    return true;
                }
                pos2Selections.put(player.getUniqueId(), player.getLocation());
                plugin.messages().send(sender, "setup-saved", Map.of("target", "pos2"));
                return true;
            }
            case "region" -> {
                if (!(sender instanceof Player player) || args.length < 4) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee map region <EVENT> <mapId>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(
                        type, args[3]
                );
                Location pos1 = pos1Selections.get(player.getUniqueId());
                Location pos2 = pos2Selections.get(player.getUniqueId());
                if (map.isEmpty() || pos1 == null || pos2 == null) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "missing map or region positions"));
                    return true;
                }
                map.get().region(CuboidRegion.fromCorners(pos1, pos2));
                mapSetupService.save();
                plugin.messages().send(sender, "setup-saved", Map.of("target", "region for " + map.get().id()));
                return true;
            }
            case "spawn" -> {
                if (!(sender instanceof Player player) || args.length < 5) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee map spawn <EVENT> <mapId> <spawnId>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(
                        type, args[3]
                );
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                map.get().spawns().put(args[4].toLowerCase(Locale.ROOT), player.getLocation());
                mapSetupService.save();
                plugin.messages().send(sender, "setup-saved", Map.of("target", "spawn " + args[4]));
                return true;
            }
            case "spectator" -> {
                if (!(sender instanceof Player player) || args.length < 4) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee map spectator <EVENT> <mapId>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(
                        type, args[3]
                );
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                map.get().spectatorSpawn(player.getLocation());
                mapSetupService.save();
                plugin.messages().send(sender, "setup-saved", Map.of("target", "spectator spawn"));
                return true;
            }
            case "checkpoint" -> {
                if (!(sender instanceof Player player) || args.length < 5) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee map checkpoint <EVENT> <mapId> <checkpointId>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(
                        type, args[3]
                );
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                map.get().checkpoints().put(args[4].toLowerCase(Locale.ROOT), player.getLocation());
                mapSetupService.save();
                plugin.messages().send(sender, "setup-saved", Map.of("target", "checkpoint " + args[4]));
                return true;
            }
            case "list" -> {
                if (args.length < 3) {
                    sender.sendMessage("/ee map list <EVENT>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                String maps = mapSetupService.mapsFor(type).stream().map(EventMap::id).collect(Collectors.joining(", "));
                plugin.messages().send(sender, "map-list-entry", Map.of("event", type.name(), "maps", maps.isBlank() ? "none" : maps));
                return true;
            }
            case "tp", "teleport" -> {
                if (!(sender instanceof Player player) || args.length < 4) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee map tp <EVENT> <mapId>");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(type, args[3]);
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                Location target = mapTeleportLocation(map.get());
                if (target == null) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map has no loaded world or usable location"));
                    return true;
                }
                TeleportService.teleport(plugin, player, target, "admin map teleport");
                plugin.messages().send(sender, "setup-saved", Map.of("target", "teleport to " + map.get().id()));
                return true;
            }
            case "export" -> {
                if (args.length < 4) {
                    sender.sendMessage("/ee map export <EVENT> <mapId> [worldName]");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(type, args[3]);
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                String worldName = args.length >= 5 ? args[4] : null;
                if (worldName == null || worldName.isBlank()) {
                    worldName = "ee_" + type.name().toLowerCase(Locale.ROOT) + "_" + args[3].toLowerCase(Locale.ROOT);
                }
                mapCopyService.exportMap(sender, map.get(), worldName);
                return true;
            }
            case "status", "worldstatus" -> {
                mapCopyService.sendWorldStatus(sender);
                return true;
            }
            case "transfer" -> {
                if (args.length < 4) {
                    sender.sendMessage("/ee map transfer <EVENT> <mapId> [worldName] [confirm]");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(type, args[3]);
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                String worldName = null;
                boolean confirmed = false;
                if (args.length >= 5) {
                    if (args[4].equalsIgnoreCase("confirm")) {
                        confirmed = true;
                    } else {
                        worldName = args[4];
                    }
                }
                if (args.length >= 6 && args[5].equalsIgnoreCase("confirm")) {
                    confirmed = true;
                }
                mapCopyService.transferMap(sender, map.get(), worldName, confirmed);
                return true;
            }
            case "transferall" -> {
                mapCopyService.transferAllMaps(sender);
                return true;
            }
            case "exportall" -> {
                mapCopyService.exportAll(sender);
                return true;
            }
            case "exporthub" -> {
                String worldName = args.length >= 3 ? args[2] : null;
                mapCopyService.exportHub(sender, worldName);
                return true;
            }
            case "exporttrophy" -> {
                String worldName = args.length >= 3 ? args[2] : null;
                mapCopyService.exportTrophy(sender, worldName);
                return true;
            }
            case "exportglobal" -> {
                mapCopyService.exportGlobalAreas(sender);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleKitCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/ee kit <save|list|select|override>");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "save" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee kit save <name>");
                    return true;
                }
                if (kitService.saveKit(player, args[2])) {
                    plugin.messages().send(sender, "kit-saved", Map.of("kit", args[2]));
                } else {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "kit name can only contain letters, numbers, underscore, or dash"));
                }
                return true;
            }
            case "list" -> {
                String kits = kitService.kits().stream().map(EventKitService.EventKit::name).collect(Collectors.joining(", "));
                plugin.messages().send(sender, "kit-list", Map.of("kits", kits.isBlank() ? "none" : kits));
                return true;
            }
            case "select" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    if (!(sender instanceof Player)) {
                        plugin.messages().send(sender, "player-only");
                        return true;
                    }
                    sender.sendMessage("/ee kit select <name>");
                    return true;
                }
                if (kitService.selectKit(player, args[2])) {
                    plugin.messages().send(sender, "kit-selected", Map.of("kit", args[2]));
                } else {
                    plugin.messages().send(sender, "kit-not-found", Map.of("kit", args[2]));
                }
                return true;
            }
            case "override" -> {
                if (args.length < 4) {
                    sender.sendMessage("/ee kit override <player> <kit>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    plugin.messages().send(sender, "event-no-snapshot");
                    return true;
                }
                if (kitService.selectKit(target, args[3])) {
                    plugin.messages().send(sender, "kit-override", Map.of("player", target.getName(), "kit", args[3]));
                } else {
                    plugin.messages().send(sender, "kit-not-found", Map.of("kit", args[3]));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private List<String> filter(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Location mapTeleportLocation(EventMap map) {
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

    private EventType parseEvent(CommandSender sender, String raw) {
        try {
            return EventType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "invalid-event", Map.of("event", raw));
            return null;
        }
    }

    private EventType parseEventSilently(String raw) {
        try {
            return EventType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
