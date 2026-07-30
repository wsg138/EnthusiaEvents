package org.enthusia.events.command;

import org.bukkit.Location;
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
    private final Map<UUID, Location> pos1Selections = new HashMap<>();
    private final Map<UUID, Location> pos2Selections = new HashMap<>();
    private final Map<String, AdminCommandAction> actions;

    AdminMapCommandHandler(EnthusiaEventsPlugin plugin, EventManager eventManager,
                           MapSetupService mapSetupService, MapCopyService mapCopyService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.mapSetupService = mapSetupService;
        AdminMapTransferCommandHandler transferHandler = new AdminMapTransferCommandHandler(
                plugin, mapSetupService, mapCopyService
        );
        this.actions = Map.ofEntries(
                Map.entry("clear", this::clear),
                Map.entry("create", this::create),
                Map.entry("pos1", this::selectPos1),
                Map.entry("pos2", this::selectPos2),
                Map.entry("region", this::saveRegion),
                Map.entry("spawn", this::saveSpawn),
                Map.entry("spectator", this::saveSpectatorSpawn),
                Map.entry("checkpoint", this::saveCheckpoint),
                Map.entry("list", this::list),
                Map.entry("tp", this::teleport),
                Map.entry("teleport", this::teleport),
                Map.entry("export", transferHandler::export),
                Map.entry("status", transferHandler::status),
                Map.entry("worldstatus", transferHandler::status),
                Map.entry("retarget", transferHandler::retarget),
                Map.entry("transfer", transferHandler::transfer),
                Map.entry("transferall", transferHandler::transferAll),
                Map.entry("exportall", transferHandler::exportAll),
                Map.entry("exporthub", transferHandler::exportHub),
                Map.entry("exporttrophy", transferHandler::exportTrophy),
                Map.entry("exportglobal", transferHandler::exportGlobal)
        );
    }

    boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        AdminCommandAction action = actions.get(args[1].toLowerCase(Locale.ROOT));
        return action != null && action.execute(sender, args);
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

    private boolean selectPos1(CommandSender sender, String[] args) {
        return selectPosition(sender, pos1Selections, "pos1");
    }

    private boolean selectPos2(CommandSender sender, String[] args) {
        return selectPosition(sender, pos2Selections, "pos2");
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
}
