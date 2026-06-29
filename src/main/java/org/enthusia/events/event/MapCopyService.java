package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.LocationCodec;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;

public final class MapCopyService {

    private static final int BLOCKS_PER_TICK = 2_000;
    private static final int STATUS_PAGE_SIZE = 6;
    private static final String EXPORT_FAILED = "map-export-failed";
    private static final String REASON = "reason";
    private static final String ANOTHER_EXPORT_RUNNING = "another export is already running";

    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService mapSetupService;
    private boolean running;

    public MapCopyService(EnthusiaEventsPlugin plugin, MapSetupService mapSetupService) {
        this.plugin = plugin;
        this.mapSetupService = mapSetupService;
    }

    public boolean exportMap(CommandSender sender, EventMap map, String worldName) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(mapRequest(map, worldName, true))));
    }

    public boolean exportEventMaps(CommandSender sender, EventType eventType) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
            return false;
        }
        List<EventMap> maps = mapSetupService.mapsFor(eventType);
        if (maps.isEmpty()) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, "no maps found for " + eventType.name()));
            return false;
        }
        Queue<CopyRequest> requests = maps.stream()
                .map(map -> mapRequest(map, defaultWorldName(map), true))
                .collect(java.util.stream.Collectors.toCollection(ArrayDeque::new));
        return startQueue(sender, requests);
    }

    public void sendWorldStatus(CommandSender sender, int page) {
        List<EventMap> maps = mapSetupService.allMaps();
        if (maps.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No event maps are configured.");
            return;
        }
        List<MapWorldStatus> statuses = maps.stream()
                .sorted(Comparator.comparing((EventMap map) -> map.eventType().name()).thenComparing(EventMap::id))
                .map(map -> new MapWorldStatus(map, isInDedicatedWorld(map)))
                .toList();
        long ownWorlds = statuses.stream().filter(MapWorldStatus::dedicated).count();
        List<MapWorldStatus> needsTransfer = statuses.stream().filter(status -> !status.dedicated()).toList();
        List<MapWorldStatus> ownWorldList = statuses.stream().filter(MapWorldStatus::dedicated).toList();
        List<MapWorldStatus> display = needsTransfer.isEmpty() ? ownWorldList : needsTransfer;
        int maxPage = Math.max(1, (int) Math.ceil(display.size() / (double) STATUS_PAGE_SIZE));
        int safePage = Math.max(1, Math.min(page, maxPage));
        int from = (safePage - 1) * STATUS_PAGE_SIZE;
        int to = Math.min(display.size(), from + STATUS_PAGE_SIZE);

        sender.sendMessage(ChatColor.DARK_GRAY + "----------------------------------------");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Event Map Worlds"
                + ChatColor.GRAY + "  Page " + safePage + "/" + maxPage);
        sender.sendMessage(ChatColor.GRAY + "Own worlds: " + ChatColor.GREEN + ownWorlds
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Need transfer: "
                + (needsTransfer.isEmpty() ? ChatColor.GREEN : ChatColor.YELLOW) + needsTransfer.size());
        sender.sendMessage(ChatColor.DARK_GRAY + "----------------------------------------");

        if (!needsTransfer.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "Needs Transfer");
        } else {
            sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Already In Own Worlds");
        }
        for (int i = from; i < to; i++) {
            sendStatusLine(sender, display.get(i));
        }
        if (display.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "Every configured map appears to be in its own world.");
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "----------------------------------------");
        sender.sendMessage(ChatColor.GRAY + "Commands: " + ChatColor.AQUA + "/ee map transferall"
                + ChatColor.DARK_GRAY + " | " + ChatColor.AQUA + "/ee map status " + (safePage + 1));
    }

    private void sendStatusLine(CommandSender sender, MapWorldStatus status) {
        EventMap map = status.map();
        ChatColor stateColor = status.dedicated() ? ChatColor.GREEN : ChatColor.YELLOW;
        String state = status.dedicated() ? "OWN" : "MOVE";
        sender.sendMessage(stateColor + "[" + state + "] " + ChatColor.WHITE + map.eventType().name()
                + ChatColor.GRAY + " / " + ChatColor.AQUA + map.id());
        sender.sendMessage(ChatColor.DARK_GRAY + "  Current: " + ChatColor.GRAY + nullToUnset(map.worldName()));
        if (!status.dedicated()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  Target:  " + ChatColor.GRAY + defaultWorldName(map));
        }
    }

    public boolean transferMap(CommandSender sender, EventMap map, String requestedWorldName, boolean confirmed) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
            return false;
        }
        boolean dedicated = isInDedicatedWorld(map);
        String targetWorld = requestedWorldName == null || requestedWorldName.isBlank()
                ? nextAvailableWorldName(defaultWorldName(map), dedicated)
                : sanitizeWorldName(requestedWorldName);
        if (dedicated && !confirmed) {
            sender.sendMessage("Map " + map.eventType().name() + " " + map.id()
                    + " already appears to be in its own world (" + map.worldName() + ").");
            sender.sendMessage("Run /ee map transfer " + map.eventType().name() + " " + map.id()
                    + " " + targetWorld + " confirm to copy it into a new world anyway.");
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(mapRequest(map, targetWorld, true))));
    }

    public boolean transferAllMaps(CommandSender sender) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
            return false;
        }
        List<CopyRequest> requests = mapSetupService.allMaps().stream()
                .filter(map -> !isInDedicatedWorld(map))
                .map(map -> mapRequest(map, nextAvailableWorldName(defaultWorldName(map), false), true))
                .toList();
        if (requests.isEmpty()) {
            sender.sendMessage("All configured event maps already appear to be in their own worlds.");
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(requests));
    }

    public boolean exportHub(CommandSender sender, String worldName) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
            return false;
        }
        CopyRequest request = configuredAreaRequest(
                "waiting hub",
                "locations.waiting-hub",
                "locations.waiting-hub-region",
                worldName == null || worldName.isBlank() ? "ee_waiting_hub" : worldName
        );
        if (request == null) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, "waiting hub location and region must be set first"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(request)));
    }

    public boolean exportTrophy(CommandSender sender, String worldName) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
            return false;
        }
        CopyRequest request = configuredAreaRequest(
                "trophy room",
                "locations.trophy-room",
                "locations.trophy-room-region",
                worldName == null || worldName.isBlank() ? "ee_trophy_room" : worldName
        );
        if (request == null) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, "trophy room location and region must be set first"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(List.of(request)));
    }

    public boolean exportGlobalAreas(CommandSender sender) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
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
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, "hub and trophy room locations/regions must be set first"));
            return false;
        }
        return startQueue(sender, new ArrayDeque<>(requests));
    }

    public boolean exportAll(CommandSender sender) {
        if (running) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, ANOTHER_EXPORT_RUNNING));
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
        for (EventMap map : mapSetupService.allMaps().stream()
                .filter(map -> map.eventType() != EventType.BOAT_RACE)
                .filter(map -> !isInDedicatedWorld(map))
                .toList()) {
            requests.add(mapRequest(map, defaultWorldName(map), true));
        }
        if (requests.isEmpty()) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, "no configured maps found"));
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
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, request.label() + " has no region"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        World source = Bukkit.getWorld(request.region().worldName());
        if (source == null) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, request.label() + " source world is not loaded"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        if (source.getName().equalsIgnoreCase(request.worldName())) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, request.label() + " target world matches the source world"));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        if (request.requireUnusedWorld() && worldExists(request.worldName())) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON,
                    request.label() + " target world already exists: " + request.worldName()));
            Bukkit.getScheduler().runTask(plugin, () -> runNext(sender, requests));
            return;
        }
        WorldCreator creator = new WorldCreator(request.worldName());
        if (request.voidWorld()) {
            creator.generator(new VoidWorldGenerator());
        }
        World target = Bukkit.createWorld(creator);
        if (target == null) {
            plugin.messages().send(sender, EXPORT_FAILED, java.util.Map.of(REASON, "target world could not be created"));
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

    private CopyRequest mapRequest(EventMap map, String worldName, boolean requireUnusedWorld) {
        return new CopyRequest(
                map.eventType().name() + "/" + map.id(),
                sanitizeWorldName(worldName),
                map.region(),
                true,
                requireUnusedWorld,
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
        return new CopyRequest(label, sanitizeWorldName(worldName), region, true, true, target -> {
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

    public String defaultWorldName(EventMap map) {
        List<EventMap> eventMaps = mapSetupService.allMaps().stream()
                .filter(candidate -> candidate.eventType() == map.eventType())
                .sorted(Comparator.comparing(EventMap::id))
                .toList();
        String base = "Events-" + displayEventName(map.eventType());
        if (eventMaps.size() <= 1) {
            return base;
        }
        int index = 0;
        for (int i = 0; i < eventMaps.size(); i++) {
            if (eventMaps.get(i).id().equalsIgnoreCase(map.id())) {
                index = i;
                break;
            }
        }
        return base + "-" + (index + 1);
    }

    public boolean isInDedicatedWorld(EventMap map) {
        String worldName = map.worldName();
        if (worldName == null || worldName.isBlank() || !mapReferencesWorld(map, worldName)) {
            return false;
        }
        long mapsInWorld = mapSetupService.allMaps().stream()
                .filter(candidate -> candidate.worldName() != null)
                .filter(candidate -> candidate.worldName().equalsIgnoreCase(worldName))
                .count();
        return mapsInWorld == 1;
    }

    private boolean mapReferencesWorld(EventMap map, String worldName) {
        return regionWorldMatches(map.region(), worldName)
                && locationWorldMatches(map.spectatorSpawn(), worldName)
                && map.spawns().values().stream().allMatch(location -> locationWorldMatches(location, worldName))
                && map.checkpoints().values().stream().allMatch(location -> locationWorldMatches(location, worldName))
                && map.checkpointBlocks().values().stream()
                .flatMap(List::stream)
                .allMatch(location -> locationWorldMatches(location, worldName))
                && map.points().values().stream().allMatch(location -> locationWorldMatches(location, worldName))
                && map.areas().values().stream().allMatch(area -> regionWorldMatches(area, worldName))
                && map.chests().values().stream()
                .flatMap(List::stream)
                .allMatch(location -> locationWorldMatches(location, worldName))
                && map.generators().values().stream()
                .flatMap(List::stream)
                .allMatch(location -> locationWorldMatches(location, worldName));
    }

    private boolean locationWorldMatches(Location location, String worldName) {
        if (location == null) {
            return true;
        }
        return location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(worldName);
    }

    private boolean regionWorldMatches(CuboidRegion region, String worldName) {
        return region == null || region.worldName().equalsIgnoreCase(worldName);
    }

    private String nextAvailableWorldName(String baseName, boolean forceSuffix) {
        String sanitized = sanitizeWorldName(baseName);
        if (!forceSuffix && !worldExists(sanitized)) {
            return sanitized;
        }
        int suffix = 2;
        String candidate = sanitized + "-" + suffix;
        while (worldExists(candidate)) {
            suffix++;
            candidate = sanitized + "-" + suffix;
        }
        return candidate;
    }

    private boolean worldExists(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }
        return new File(Bukkit.getWorldContainer(), worldName).exists();
    }

    private String sanitizeWorldName(String worldName) {
        String sanitized = Optional.ofNullable(worldName).orElse("")
                .replaceAll("[^A-Za-z0-9_\\-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "Events-Map" : sanitized;
    }

    private String displayEventName(EventType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String nullToUnset(String value) {
        return value == null || value.isBlank() ? "unset" : value;
    }

    private record CopyRequest(String label, String worldName, CuboidRegion region, boolean voidWorld,
                               boolean requireUnusedWorld, Consumer<World> onComplete) {
    }

    private record MapWorldStatus(EventMap map, boolean dedicated) {
    }
}
