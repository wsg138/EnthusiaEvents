package org.enthusia.events.event;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EventMap {

    private final EventType eventType;
    private final String id;
    private String worldName;
    private CuboidRegion region;
    private Location spectatorSpawn;
    private final Map<String, Location> spawns = new LinkedHashMap<>();
    private final Map<String, Location> checkpoints = new LinkedHashMap<>();
    private final Map<String, Location> points = new LinkedHashMap<>();
    private final Map<String, CuboidRegion> areas = new LinkedHashMap<>();
    private final Map<Integer, List<Location>> chests = new LinkedHashMap<>();
    private final Map<String, List<Location>> generators = new LinkedHashMap<>();

    public EventMap(EventType eventType, String id) {
        this.eventType = eventType;
        this.id = id;
    }

    public EventType eventType() {
        return eventType;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName;
    }

    public CuboidRegion region() {
        return region;
    }

    public void region(CuboidRegion region) {
        this.region = region;
    }

    public Location spectatorSpawn() {
        return spectatorSpawn;
    }

    public void spectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
    }

    public Map<String, Location> spawns() {
        return spawns;
    }

    public Map<String, Location> checkpoints() {
        return checkpoints;
    }

    public Map<String, Location> points() {
        return points;
    }

    public Map<String, CuboidRegion> areas() {
        return areas;
    }

    public Map<Integer, List<Location>> chests() {
        return chests;
    }

    public Map<String, List<Location>> generators() {
        return generators;
    }

    public void addChest(int tier, Location location) {
        chests.computeIfAbsent(tier, ignored -> new ArrayList<>()).add(location);
    }

    public void addGenerator(String type, Location location) {
        generators.computeIfAbsent(type.toLowerCase(java.util.Locale.ROOT), ignored -> new ArrayList<>()).add(location);
    }
}
