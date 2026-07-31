package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;

import java.util.Map;
import java.util.Optional;

final class AdminMapTransferCommandHandler {

    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService mapSetupService;
    private final MapCopyService mapCopyService;

    AdminMapTransferCommandHandler(EnthusiaEventsPlugin plugin, MapSetupService mapSetupService,
                                   MapCopyService mapCopyService) {
        this.plugin = plugin;
        this.mapSetupService = mapSetupService;
        this.mapCopyService = mapCopyService;
    }

    boolean export(CommandSender sender, String[] args) {
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

    boolean status(CommandSender sender, String[] args) {
        int page = args.length >= 3 ? AdminCommandSupport.parsePositiveInt(args[2], 1) : 1;
        mapCopyService.sendWorldStatus(sender, page);
        return true;
    }

    boolean retarget(CommandSender sender, String[] args) {
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

    boolean transfer(CommandSender sender, String[] args) {
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

    boolean transferAll(CommandSender sender, String[] args) {
        mapCopyService.transferAllMaps(sender);
        return true;
    }

    boolean exportAll(CommandSender sender, String[] args) {
        mapCopyService.exportAll(sender);
        return true;
    }

    boolean exportHub(CommandSender sender, String[] args) {
        mapCopyService.exportHub(sender, args.length >= 3 ? args[2] : null);
        return true;
    }

    boolean exportTrophy(CommandSender sender, String[] args) {
        mapCopyService.exportTrophy(sender, args.length >= 3 ? args[2] : null);
        return true;
    }

    boolean exportGlobal(CommandSender sender, String[] args) {
        mapCopyService.exportGlobalAreas(sender);
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

    private boolean mapNotFound(CommandSender sender) {
        plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
        return true;
    }

    private record TransferOptions(String worldName, boolean confirmed) {
    }
}
