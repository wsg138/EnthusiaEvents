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
            case "autostart" -> {
                return handleAutoStartCommand(sender, args);
            }
            case "disable" -> {
                return handleEventToggleCommand(sender, args, true);
            }
            case "enable" -> {
                return handleEventToggleCommand(sender, args, false);
            }
            case "enabled" -> {
                List<String> enabled = eventManager.enabledEvents().stream().map(Enum::name).toList();
                plugin.messages().send(sender, "event-enabled-list", Map.of(
                        "events", enabled.isEmpty() ? "none" : String.join(", ", enabled)
                ));
            }
            case "disabled" -> {
                List<String> disabled = eventManager.disabledEvents().stream().map(Enum::name).toList();
                plugin.messages().send(sender, "event-disabled-list", Map.of(
                        "events", disabled.isEmpty() ? "none" : String.join(", ", disabled)
                ));
            }
            case "status" -> {
                plugin.messages().send(sender, "event-admin-status", Map.of("status", eventManager.adminStatusLine()));
            }
            case "private" -> {
                return handlePrivateEventCommand(sender, args);
            }
            case "invite" -> {
                return handleInviteCommand(sender, args);
            }
            case "forcestart", "simulatevote" -> {
                boolean started;
                if (args[0].equalsIgnoreCase("simulatevote")) {
                    started = eventManager.startScheduledVote();
                } else if (args.length == 1 && eventManager.isPrivateSessionWaiting()) {
                    started = eventManager.advancePhase();
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
                            ? eventManager.forcedStartFailureReason(EventType.parse(args[1]))
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
            case "eventtp", "eventteleport" -> {
                return handleEventTeleportCommand(sender, args);
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
                } else {
                    plugin.messages().send(sender, "event-restore-started", Map.of("player", target.getName()));
                    eventManager.restoreSnapshot(target).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.messages().send(sender, restored ? "event-restored" : "event-restore-failed-staff",
                                    Map.of("player", target.getName()))));
                }
            }
            case "stuckcheck" -> {
                if (args.length < 2) {
                    sender.sendMessage("/ee stuckcheck <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                sender.sendMessage(eventManager.stuckCheck(target));
            }
            case "emergencyrestore" -> {
                if (args.length < 2) {
                    sender.sendMessage("/ee emergencyrestore <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                plugin.messages().send(sender, "event-emergency-restore-started", Map.of("player", target.getName()));
                eventManager.emergencyRestore(target).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.messages().send(sender, restored ? "event-emergency-restore-done" : "event-emergency-restore-partial",
                                Map.of("player", target.getName()))));
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
                if (plugin.reloadPlugin()) {
                    plugin.messages().send(sender, "reload-done");
                } else {
                    plugin.messages().send(sender, "reload-blocked-active-event");
                }
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
            return filter(args[0], List.of("autostart", "disable", "enable", "enabled", "disabled", "status", "private", "invite", "forcestart", "simulatevote", "advance", "forcestop", "eventtp", "stop", "restore", "stuckcheck", "emergencyrestore", "remove", "reload", "retryrestores", "kit", "resetconfigs", "resetloot", "map", "setup"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("disable")
                || args[0].equalsIgnoreCase("enable")
                || args[0].equalsIgnoreCase("private"))) {
            return filter(args[1], List.of(EventType.values()).stream().map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("autostart")) {
            return filter(args[1], List.of("on", "off", "status"));
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("private")) {
            return filter(args[args.length - 1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("eventtp") || args[0].equalsIgnoreCase("eventteleport"))) {
            return filter(args[1], List.of(EventType.values()).stream().map(Enum::name).toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("eventtp") || args[0].equalsIgnoreCase("eventteleport"))) {
            EventType type = parseEventSilently(args[1]);
            if (type != null) {
                return filter(args[2], mapSetupService.mapsFor(type).stream().map(EventMap::id).toList());
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            List<String> values = new ArrayList<>();
            values.add("cancel");
            values.addAll(List.of(EventType.values()).stream().map(Enum::name).toList());
            return filter(args[1], values);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup")) {
            EventType type = parseEventSilently(args[1]);
            return type == null ? List.of() : mapIds(type);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setup")) {
            return filter(args[3], List.of("pos1", "pos2", "spawn", "spectator", "checkpoint", "checkpoint_spawn", "finish", "chest", "generator", "point", "area_pos1", "area_pos2", "area"));
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("setup")) {
            EventType type = parseEventSilently(args[1]);
            EventMap map = type == null ? null : mapSetupService.find(type, args[2]).orElse(null);
            return setupValues(type, map, args[3]);
        }
        if (args.length == 2 && List.of("restore", "stuckcheck", "emergencyrestore", "remove").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("map")) {
            return filter(args[1], List.of("create", "clear", "pos1", "pos2", "region", "spawn", "spectator", "checkpoint", "list", "tp", "retarget", "status", "transfer", "transferall", "export", "exporthub", "exporttrophy", "exportglobal", "exportall"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("map") && mapSubcommandNeedsEvent(args[1])) {
            return filter(args[2], List.of(EventType.values()).stream().map(Enum::name).toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("map") && mapSubcommandNeedsMapId(args[1])) {
            EventType type = parseEventSilently(args[2]);
            if (type != null) {
                return filter(args[3], mapIds(type));
            }
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("clear")) {
            return filter(args[4], List.of("all", "region", "spectator", "spawns", "finishes", "checkpoints",
                    "checkpoint-blocks", "points", "beds", "shops", "areas", "chests", "generators"));
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("clear")
                && args[4].equalsIgnoreCase("all")) {
            return filter(args[5], List.of("confirm"));
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("retarget")) {
            return filter(args[4], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("map")) {
            EventType type = parseEventSilently(args[2]);
            EventMap map = type == null ? null : mapSetupService.find(type, args[3]).orElse(null);
            if (args[1].equalsIgnoreCase("spawn")) {
                return filter(args[4], spawnIds(map));
            }
            if (args[1].equalsIgnoreCase("checkpoint")) {
                return filter(args[4], checkpointIds(map));
            }
            if (args[1].equalsIgnoreCase("transfer")) {
                return filter(args[4], List.of("confirm"));
            }
            if (args[1].equalsIgnoreCase("export") && map != null) {
                return filter(args[4], List.of(mapCopyService.defaultWorldName(map)));
            }
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("transfer")) {
            return filter(args[5], List.of("confirm"));
        }
        return List.of();
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
        String tool = rawTool.toLowerCase(Locale.ROOT);
        return switch (tool) {
            case "spawn" -> spawnIds(map);
            case "checkpoint" -> checkpointIds(map);
            case "checkpoint_spawn" -> map == null ? List.of("checkpoint-spawn-1") : map.checkpoints().keySet().stream()
                    .filter(key -> !key.startsWith("finish"))
                    .map(key -> "checkpoint-spawn-" + key)
                    .toList();
            case "finish" -> List.of("finish-1", "finish-2");
            case "chest" -> List.of("1", "2", "3");
            case "generator" -> generatorIds(type, map);
            case "point" -> pointIds(type, map);
            case "area" -> areaIds(type, map);
            default -> List.of();
        };
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

    private boolean handleAutoStartCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            plugin.messages().send(sender, "event-autostart-status", Map.of(
                    "state", plugin.getConfig().getBoolean("schedule.enabled", false) ? "enabled" : "disabled"
            ));
            return true;
        }
        boolean enabled;
        if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("enabled")) {
            enabled = true;
        } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("disable") || args[1].equalsIgnoreCase("disabled")) {
            enabled = false;
        } else {
            sender.sendMessage("/ee autostart <on|off|status>");
            return true;
        }
        plugin.getConfig().set("schedule.enabled", enabled);
        plugin.saveConfig();
        plugin.messages().send(sender, "event-autostart-updated", Map.of("state", enabled ? "enabled" : "disabled"));
        return true;
    }

    private boolean handleEventToggleCommand(CommandSender sender, String[] args, boolean disabled) {
        if (args.length < 2) {
            sender.sendMessage(disabled ? "/ee disable <EVENT>" : "/ee enable <EVENT>");
            return true;
        }
        EventType type = parseEvent(sender, args[1]);
        if (type == null) {
            return true;
        }
        eventManager.setEventDisabled(type, disabled);
        plugin.messages().send(sender, disabled ? "event-disabled" : "event-enabled", Map.of("event", type.name()));
        return true;
    }

    private boolean handlePrivateEventCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/ee private <EVENT> [player...]");
            return true;
        }
        EventType type = parseEvent(sender, args[1]);
        if (type == null) {
            return true;
        }
        List<Player> invited = new ArrayList<>();
        if (sender instanceof Player player) {
            invited.add(player);
        }
        for (int index = 2; index < args.length; index++) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", Map.of("player", args[index]));
                return true;
            }
            if (!invited.contains(target)) {
                invited.add(target);
            }
        }
        if (!eventManager.startPrivateEvent(sender, type, invited)) {
            plugin.messages().send(sender, "force-start-failed", Map.of("reason", eventManager.privateStartFailureReason(type)));
            return true;
        }
        plugin.messages().send(sender, "private-event-started", Map.of(
                "event", type.name(),
                "players", invited.isEmpty() ? "none yet" : invited.stream().map(Player::getName).collect(Collectors.joining(", "))
        ));
        return true;
    }

    private boolean handleEventTeleportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/ee eventtp <EVENT> [mapId]");
            return true;
        }
        EventType type = parseEvent(sender, args[1]);
        if (type == null) {
            return true;
        }
        Optional<EventMap> map = args.length >= 3
                ? mapSetupService.find(type, args[2])
                : mapSetupService.mapsFor(type).stream().findFirst();
        if (map.isEmpty()) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
            return true;
        }
        Location target = mapTeleportLocation(map.get());
        if (target == null) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map has no loaded world or usable location"));
            return true;
        }
        TeleportService.teleport(plugin, player, target, "admin event map teleport");
        plugin.messages().send(sender, "setup-saved", Map.of("target", "teleport to " + type.name() + " map " + map.get().id()));
        return true;
    }

    private boolean handleInviteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/ee invite <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }
        if (!eventManager.invitePrivatePlayer(target)) {
            plugin.messages().send(sender, "private-event-invite-failed");
            return true;
        }
        plugin.messages().send(sender, "private-event-player-invited", Map.of("player", target.getName()));
        return true;
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
        if (args.length < 3) {
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
        if (args.length == 3) {
            setupWizard.openPalette(player, eventType, args[2]);
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
            case "clear" -> {
                if (args.length < 5) {
                    sender.sendMessage("/ee map clear <EVENT> <mapId> <all|region|spectator|spawns|finishes|checkpoints|checkpoint-blocks|points|beds|shops|areas|chests|generators> [confirm]");
                    return true;
                }
                if (eventManager.session() != null) {
                    plugin.messages().send(sender, "setup-failed", Map.of(
                            "reason", "stop the active event session before clearing map setup"
                    ));
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
                String section = args[4].toLowerCase(Locale.ROOT);
                if (section.equals("all") && (args.length < 6 || !args[5].equalsIgnoreCase("confirm"))) {
                    plugin.messages().send(sender, "map-clear-confirm", Map.of(
                            "event", type.name(), "map", map.get().id()
                    ));
                    return true;
                }
                int removed = mapSetupService.clearMapSection(map.get(), section);
                if (removed < 0) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "unknown setup section: " + section));
                    return true;
                }
                plugin.messages().send(sender, "map-clear-done", Map.of(
                        "event", type.name(), "map", map.get().id(), "section", section,
                        "count", String.valueOf(removed)
                ));
                return true;
            }
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
                if (args.length < 3) {
                    sender.sendMessage("/ee map export <EVENT> [mapId] [worldName]");
                    return true;
                }
                EventType type = parseEvent(sender, args[2]);
                if (type == null) {
                    return true;
                }
                if (args.length == 3) {
                    mapCopyService.exportEventMaps(sender, type);
                    return true;
                }
                Optional<EventMap> map = mapSetupService.find(type, args[3]);
                if (map.isEmpty()) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
                    return true;
                }
                String worldName = args.length >= 5 ? args[4] : null;
                if (worldName == null || worldName.isBlank()) {
                    worldName = mapCopyService.defaultWorldName(map.get());
                }
                mapCopyService.exportMap(sender, map.get(), worldName);
                return true;
            }
            case "status", "worldstatus" -> {
                int page = args.length >= 3 ? parsePositiveInt(args[2], 1) : 1;
                mapCopyService.sendWorldStatus(sender, page);
                return true;
            }
            case "retarget" -> {
                if (args.length < 5) {
                    sender.sendMessage("/ee map retarget <EVENT> <mapId> <worldName>");
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
                World world = Bukkit.getWorld(args[4]);
                if (world == null) {
                    world = Bukkit.createWorld(new WorldCreator(args[4]));
                }
                if (world == null) {
                    plugin.messages().send(sender, "setup-failed", Map.of("reason", "world could not be loaded"));
                    return true;
                }
                mapSetupService.retargetWorld(map.get(), world);
                plugin.messages().send(sender, "setup-saved", Map.of("target", "retarget " + map.get().id() + " to " + world.getName()));
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
            return EventType.parse(raw);
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "invalid-event", Map.of("event", raw));
            return null;
        }
    }

    private EventType parseEventSilently(String raw) {
        try {
            return EventType.parse(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw);
            return Math.max(1, value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
