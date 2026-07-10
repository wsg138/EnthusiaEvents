package org.enthusia.events.event;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventMap {

    private final EventType mapEventType;
    private final String mapId;
    private String mapWorldName;
    private CuboidRegion mapRegion;
    private Location spectatorLocation;
    private final Map<String, Location> spawnLocations = new LinkedHashMap<>();
    private final Map<String, Location> checkpointLocations = new LinkedHashMap<>();
    private final Map<String, List<Location>> checkpointBlockLocations = new LinkedHashMap<>();
    private final Map<String, Location> pointLocations = new LinkedHashMap<>();
    private final Map<String, String> pointBlockData = new LinkedHashMap<>();
    private final Map<String, CuboidRegion> namedAreas = new LinkedHashMap<>();
    private final Map<Integer, List<Location>> chestLocations = new LinkedHashMap<>();
    private final Map<String, List<Location>> generatorLocations = new LinkedHashMap<>();

    public EventMap(EventType eventType, String id) {
        this.mapEventType = eventType;
        this.mapId = id;
    }

    public EventType eventType() {
        return mapEventType;
    }

    public String id() {
        return mapId;
    }

    public String worldName() {
        return mapWorldName;
    }

    public void worldName(String worldName) {
        this.mapWorldName = worldName;
    }

    public CuboidRegion region() {
        return mapRegion;
    }

    public void region(CuboidRegion region) {
        this.mapRegion = region;
    }

    public Location spectatorSpawn() {
        return spectatorLocation;
    }

    public void spectatorSpawn(Location spectatorSpawn) {
        this.spectatorLocation = spectatorSpawn;
    }

    public Map<String, Location> spawns() {
        return spawnLocations;
    }

    public Map<String, Location> checkpoints() {
        return checkpointLocations;
    }

    public Map<String, List<Location>> checkpointBlocks() {
        return checkpointBlockLocations;
    }

    public void addCheckpointBlock(String checkpointId, Location location) {
        checkpointBlockLocations.computeIfAbsent(checkpointId, ignored -> new ArrayList<>()).add(location);
    }

    public Map<String, Location> points() {
        return pointLocations;
    }

    public Map<String, String> pointBlockData() {
        return pointBlockData;
    }

    public Map<String, CuboidRegion> areas() {
        return namedAreas;
    }

    public Map<Integer, List<Location>> chests() {
        return chestLocations;
    }

    public Map<String, List<Location>> generators() {
        return generatorLocations;
    }

    public void addChest(int tier, Location location) {
        chestLocations.computeIfAbsent(tier, ignored -> new ArrayList<>()).add(location);
    }

    public void addGenerator(String type, Location location) {
        generatorLocations.computeIfAbsent(type.toLowerCase(java.util.Locale.ROOT), ignored -> new ArrayList<>()).add(location);
    }
}
