package org.enthusia.events.event;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EventSession {

    private EventDefinition definition;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<EventType, Integer> votes = new LinkedHashMap<>();
    private EventPhase phase;
    private Location waitingHub;
    private Location trophyRoom;
    private EventMap selectedMap;
    private String startedBy;
    private boolean adminStarted;
    private final Map<UUID, String> teams = new LinkedHashMap<>();
    private final List<UUID> finalRankings = new ArrayList<>();

    public EventSession(EventDefinition definition, EventPhase phase) {
        this.definition = definition;
        this.phase = phase;
    }

    public EventDefinition definition() {
        return definition;
    }

    public void definition(EventDefinition definition) {
        this.definition = definition;
    }

    public Set<UUID> participants() {
        return participants;
    }

    public Set<UUID> spectators() {
        return spectators;
    }

    public Map<EventType, Integer> votes() {
        return votes;
    }

    public EventPhase phase() {
        return phase;
    }

    public void phase(EventPhase phase) {
        this.phase = phase;
    }

    public Location waitingHub() {
        return waitingHub;
    }

    public void waitingHub(Location waitingHub) {
        this.waitingHub = waitingHub;
    }

    public Location trophyRoom() {
        return trophyRoom;
    }

    public void trophyRoom(Location trophyRoom) {
        this.trophyRoom = trophyRoom;
    }

    public EventMap selectedMap() {
        return selectedMap;
    }

    public void selectedMap(EventMap selectedMap) {
        this.selectedMap = selectedMap;
    }

    public String startedBy() {
        return startedBy;
    }

    public void startedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public boolean adminStarted() {
        return adminStarted;
    }

    public void adminStarted(boolean adminStarted) {
        this.adminStarted = adminStarted;
    }

    public Map<UUID, String> teams() {
        return teams;
    }

    public List<UUID> finalRankings() {
        return finalRankings;
    }
}
