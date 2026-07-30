package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.CuboidRegion;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.util.TeleportService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

final class AdminMapCommandHandler {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final MapSetupService mapSetupService;
    private final MapCopyService mapCopyService;
    private final Map<UUID, Location> pos1Selections = new HashMap<>();
    private final Map<UUID, Location> pos2Selections = new HashMap<>();

    AdminMapCommandHandler(EnthusiaEventsPlugin plugin, EventManager eventManager,
                           MapSetupService mapSetupService, MapCopyService mapCopyService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.mapSetupService = mapSetupService;
        this.mapCopyService = mapCopyService;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "clear" -> clear(sender, args);
            case "create" -> create(sender, args);
            case "pos1" -> selectPosition(sender, pos1Selections, "pos1");
            case "pos2" -> selectPosition(sender, pos2Selections, "pos2");
            case "region" -> saveRegion(sender, args);
            case "spawn" -> saveSpawn(sender, args);
            case "spectator" -> saveSpectatorSpawn(sender, args);
            case "checkpoint" -> saveCheckpoint(sender, args);
            case "list" -> list(sender, args);
            case "tp", "teleport" -> teleport(sender, args);
            case "export" -> export(sender, args);
            case "status", "worldstatus" -> status(sender, args);
            case "retarget" -> retarget(sender, args);
            case "transfer" -> transfer(sender, args);
            case "transferall" -> transferAll(sender);
            case "exportall" -> exportAll(sender);
            case "exporthub" -> exportHub(sender, args);
            case "exporttrophy" -> exportTrophy(sender, args);
            case "exportglobal" -> exportGlobal(sender);
            default -> false;
        };
    }

    private boolean clear(CommandSender sender, String[] args) {
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
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[2]);
        if (type == null) {
            return true;
        }
        Optional<EventMap> map = mapSetupService.find(type, args[3]);
        if (map.isEmpty()) {
            return mapNotFound(sender);
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

    private boolean create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("/ee map create <EVENT> <mapId>");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[2]);
        if (type == null) {
            return true;
        }
        EventMap map = mapSetupService.create(type, args[3], player.getWorld().getName());
        plugin.messages().send(sender, "map-created", Map.of("map", map.id(), "event", type.name()));
        return true;
    }

    private boolean selectPosition(CommandSender sender, Map<UUID, Location> selections, String name) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        selections.put(player.getUniqueId(), player.getLocation());
        plugin.messages().send(sender, "setup-saved", Map.of("target", name));
        return true;
    }

    private boolean saveRegion(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("/ee map region <EVENT> <mapId>");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[2]);
        if (type == null) {
            return true;
        }
        Optional<EventMap> map = mapSetupService.find(type, args[3]);
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

    private boolean saveSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage("/ee map spawn <EVENT> <mapId> <spawnId>");
            return true;
        }
        Optional<EventMap> map = findMap(sender, args[2], args[3]);
        if (map.isEmpty()) {
            return true;
        }
        map.get().spawns().put(args[4].toLowerCase(Locale.ROOT), player.getLocation());
        mapSetupService.save();
        plugin.messages().send(sender, "setup-saved", Map.of("target", "spawn " + args[4]));
        return true;
    }

    private boolean saveSpectatorSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("/ee map spectator <EVENT> <mapId>");
            return true;
        }
        Optional<EventMap> map = findMap(sender, args[2], args[3]);
        if (map.isEmpty()) {
            return true;
        }
        map.get().spectatorSpawn(player.getLocation());
        mapSetupService.save();
        plugin.messages().send(sender, "setup-saved", Map.of("target", "spectator spawn"));
        return true;
    }

    private boolean saveCheckpoint(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage("/ee map checkpoint <EVENT> <mapId> <checkpointId>");
            return true;
        }
        Optional<EventMap> map = findMap(sender, args[2], args[3]);
        if (map.isEmpty()) {
            return true;
        }
        map.get().checkpoints().put(args[4].toLowerCase(Locale.ROOT), player.getLocation());
        mapSetupService.save();
        plugin.messages().send(sender, "setup-saved", Map.of("target", "checkpoint " + args[4]));
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/ee map list <EVENT>");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[2]);
        if (type == null) {
            return true;
        }
        String maps = mapSetupService.mapsFor(type).stream()
                .map(EventMap::id)
                .collect(Collectors.joining(", "));
        plugin.messages().send(sender, "map-list-entry", Map.of(
                "event", type.name(), "maps", maps.isBlank() ? "none" : maps
        ));
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("/ee map tp <EVENT> <mapId>");
            return true;
        }
        Optional<EventMap> map = findMap(sender, args[2], args[3]);
        if (map.isEmpty()) {
            return true;
        }
        Location target = AdminCommandSupport.mapTeleportLocation(map.get());
        if (target == null) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map has no loaded world or usable location"));
            return true;
        }
        TeleportService.teleport(plugin, player, target, "admin map teleport");
        plugin.messages().send(sender, "setup-saved", Map.of("target", "teleport to " + map.get().id()));
        return true;
    }

    private boolean export(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/ee map export <EVENT> [mapId] [worldName]");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[2]);
        if (type == null) {
            return true;
        }
        if (args.length == 3) {
            mapCopyService.exportEventMaps(sender, type);
            return true;
        }
        Optional<EventMap> map = mapSetupService.find(type, args[3]);
        if (map.isEmpty()) {
            return mapNotFound(sender);
        }
        String worldName = args.length >= 5 ? args[4] : mapCopyService.defaultWorldName(map.get());
        if (worldName == null || worldName.isBlank()) {
            worldName = mapCopyService.defaultWorldName(map.get());
        }
        mapCopyService.exportMap(sender, map.get(), worldName);
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        int page = args.length >= 3 ? AdminCommandSupport.parsePositiveInt(args[2], 1) : 1;
        mapCopyService.sendWorldStatus(sender, page);
        return true;
    }

    private boolean retarget(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("/ee map retarget <EVENT> <mapId> <worldName>");
            return true;
        }
        Optional<EventMap> map = findMap(sender, args[2], args[3]);
        if (map.isEmpty()) {
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
        plugin.messages().send(sender, "setup-saved", Map.of(
                "target", "retarget " + map.get().id() + " to " + world.getName()
        ));
        return true;
    }

    private boolean transfer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("/ee map transfer <EVENT> <mapId> [worldName] [confirm]");
            return true;
        }
        Optional<EventMap> map = findMap(sender, args[2], args[3]);
        if (map.isEmpty()) {
            return true;
        }
        TransferOptions options = transferOptions(args);
        mapCopyService.transferMap(sender, map.get(), options.worldName(), options.confirmed());
        return true;
    }

    private TransferOptions transferOptions(String[] args) {
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
        return new TransferOptions(worldName, confirmed);
    }

    private boolean transferAll(CommandSender sender) {
        mapCopyService.transferAllMaps(sender);
        return true;
    }

    private boolean exportAll(CommandSender sender) {
        mapCopyService.exportAll(sender);
        return true;
    }

    private boolean exportHub(CommandSender sender, String[] args) {
        mapCopyService.exportHub(sender, args.length >= 3 ? args[2] : null);
        return true;
    }

    private boolean exportTrophy(CommandSender sender, String[] args) {
        mapCopyService.exportTrophy(sender, args.length >= 3 ? args[2] : null);
        return true;
    }

    private boolean exportGlobal(CommandSender sender) {
        mapCopyService.exportGlobalAreas(sender);
        return true;
    }

    private Optional<EventMap> findMap(CommandSender sender, String rawType, String mapId) {
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, rawType);
        if (type == null) {
            return Optional.empty();
        }
        Optional<EventMap> map = mapSetupService.find(type, mapId);
        if (map.isEmpty()) {
            mapNotFound(sender);
        }
        return map;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "player-only");
        return null;
    }

    private boolean mapNotFound(CommandSender sender) {
        plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
        return true;
    }

    private record TransferOptions(String worldName, boolean confirmed) {
    }
}
