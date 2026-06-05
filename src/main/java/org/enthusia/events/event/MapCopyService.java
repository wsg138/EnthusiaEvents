package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.LocationCodec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public final class MapCopyService {

    private static final int BLOCKS_PER_TICK = 2_000;

    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService mapSetupService;
    private boolean running;

    public MapCopyService(EnthusiaEventsPlugin plugin, MapSetupService mapSetupService) {
        this.plugin = plugin;
        this.mapSetupService = mapSetupService;
    }

    public boolean exportMap(CommandSender sender, EventMap map, String worldName) {
        if (running) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "another export is already running"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(mapRequest(map, worldName))));
    }

    public boolean exportHub(CommandSender sender, String worldName) {
        if (running) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "another export is already running"));
            return false;
        }
        CopyRequest request = configuredAreaRequest(
                "waiting hub",
                "locations.waiting-hub",
                "locations.waiting-hub-region",
                worldName == null || worldName.isBlank() ? "ee_waiting_hub" : worldName
        );
        if (request == null) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "waiting hub location and region must be set first"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(request)));
    }

    public boolean exportTrophy(CommandSender sender, String worldName) {
        if (running) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "another export is already running"));
            return false;
        }
        CopyRequest request = configuredAreaRequest(
                "trophy room",
                "locations.trophy-room",
                "locations.trophy-room-region",
                worldName == null || worldName.isBlank() ? "ee_trophy_room" : worldName
        );
        if (request == null) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "trophy room location and region must be set first"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(request)));
    }

    public boolean exportGlobalAreas(CommandSender sender) {
        if (running) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "another export is already running"));
            return false;
        }
        List<CopyRequest> requests = new ArrayList<>();
        CopyRequest hub = configuredAreaRequest("waiting hub", "locations.waiting-hub", "locations.waiting-hub-region", "ee_waiting_hub");
        CopyRequest trophy = configuredAreaRequest("trophy room", "locations.trophy-room", "locations.trophy-room-region", "ee_trophy_room");
        if (hub != null) {
            requests.add(hub);
        }
        if (trophy != null) {
            requests.add(trophy);
        }
        if (requests.isEmpty()) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "hub and trophy room locations/regions must be set first"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(requests));
    }

    public boolean exportAll(CommandSender sender) {
        if (running) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "another export is already running"));
            return false;
        }
        List<CopyRequest> requests = new ArrayList<>();
        CopyRequest hub = configuredAreaRequest("waiting hub", "locations.waiting-hub", "locations.waiting-hub-region", "ee_waiting_hub");
        CopyRequest trophy = configuredAreaRequest("trophy room", "locations.trophy-room", "locations.trophy-room-region", "ee_trophy_room");
        if (hub != null) {
            requests.add(hub);
        }
        if (trophy != null) {
            requests.add(trophy);
        }
        for (EventMap map : mapSetupService.allMaps()) {
            requests.add(mapRequest(map, defaultWorldName(map)));
        }
        if (requests.isEmpty()) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "no configured maps found"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(requests));
    }

    private boolean startQueue(CommandSender sender, Queue<CopyRequest> requests) {
        running = true;
        runNext(sender, requests);
        return true;
    }

    private void runNext(CommandSender sender, Queue<CopyRequest> requests) {
        CopyRequest request = requests.poll();
        if (request == null) {
            running = false;
            plugin.messages().send(sender, "map-export-all-done");
            return;
        }
        if (request.region() == null) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", request.label() + " has no region"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        World source = Bukkit.getWorld(request.region().worldName());
        if (source == null) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", request.label() + " source world is not loaded"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        if (source.getName().equalsIgnoreCase(request.worldName())) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", request.label() + " target world matches the source world"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        World target = Bukkit.createWorld(new WorldCreator(request.worldName()));
        if (target == null) {
            plugin.messages().send(sender, "map-export-failed", java.util.Map.of("reason", "target world could not be created"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }

        CuboidRegion region = request.region();
        int minX = (int) Math.floor(region.minX());
        int minY = (int) Math.floor(region.minY());
        int minZ = (int) Math.floor(region.minZ());
        int maxX = (int) Math.floor(region.maxX());
        int maxY = (int) Math.floor(region.maxY());
        int maxZ = (int) Math.floor(region.maxZ());
        long total = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        plugin.messages().send(sender, "map-export-started", java.util.Map.of(
                "map", request.label(),
                "world", target.getName(),
                "blocks", String.valueOf(total)
        ));
        copyBatch(sender, requests, request, source, target, minX, minY, minZ, maxX, maxY, maxZ, minX, minY, minZ, 0L);
    }

    private void copyBatch(CommandSender sender, Queue<CopyRequest> requests, CopyRequest request, World source, World target,
                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                           int x, int y, int z, long copied) {
        int processed = 0;
        int cx = x;
        int cy = y;
        int cz = z;
        long done = copied;
        while (processed < BLOCKS_PER_TICK && cy <= maxY) {
            Block from = source.getBlockAt(cx, cy, cz);
            Block to = target.getBlockAt(cx, cy, cz);
            to.setBlockData(from.getBlockData(), false);
            copyContainer(from, to);

            processed++;
            done++;
            cx++;
            if (cx > maxX) {
                cx = minX;
                cz++;
            }
            if (cz > maxZ) {
                cz = minZ;
                cy++;
            }
        }
        if (cy > maxY) {
            request.onComplete().accept(target);
            plugin.messages().send(sender, "map-export-done", java.util.Map.of(
                    "map", request.label(),
                    "world", target.getName()
            ));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        int nextX = cx;
        int nextY = cy;
        int nextZ = cz;
        long nextCopied = done;
        Bukkit.getScheduler().runTaskLater(plugin, () -> copyBatch(
                sender, requests, request, source, target, minX, minY, minZ, maxX, maxY, maxZ,
                nextX, nextY, nextZ, nextCopied
        ), 1L);
    }

    private CopyRequest mapRequest(EventMap map, String worldName) {
        return new CopyRequest(
                map.id(),
                worldName,
                map.region(),
                target -> mapSetupService.retargetWorld(map, target)
        );
    }

    private CopyRequest configuredAreaRequest(String label, String locationPath, String regionPath, String worldName) {
        Location anchor = LocationCodec.decode(plugin.getConfig().getString(locationPath, ""));
        Location pos1 = LocationCodec.decode(plugin.getConfig().getString(regionPath + ".pos1", ""));
        Location pos2 = LocationCodec.decode(plugin.getConfig().getString(regionPath + ".pos2", ""));
        if (anchor == null || pos1 == null || pos2 == null) {
            return null;
        }
        CuboidRegion region;
        try {
            region = CuboidRegion.fromCorners(pos1, pos2);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid " + label + " export region: " + ex.getMessage());
            return null;
        }
        return new CopyRequest(label, worldName, region, target -> {
            plugin.getConfig().set(locationPath, LocationCodec.encode(reworld(anchor, target)));
            plugin.getConfig().set(regionPath + ".pos1", LocationCodec.encode(reworld(pos1, target)));
            plugin.getConfig().set(regionPath + ".pos2", LocationCodec.encode(reworld(pos2, target)));
            plugin.saveConfig();
        });
    }

    private void copyContainer(Block from, Block to) {
        BlockState fromState = from.getState();
        BlockState toState = to.getState();
        if (fromState instanceof Container fromContainer && toState instanceof Container toContainer) {
            toContainer.getInventory().setContents(fromContainer.getInventory().getContents());
            toContainer.update(true, false);
        }
    }

    private Location reworld(Location location, World world) {
        return new Location(
                world,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    private String defaultWorldName(EventMap map) {
        return ("ee_" + map.eventType().name() + "_" + map.id())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
    }

    private record CopyRequest(String label, String worldName, CuboidRegion region, java.util.function.Consumer<World> onComplete) {
    }
}
