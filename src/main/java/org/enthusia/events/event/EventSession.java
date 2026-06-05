package org.enthusia.events.event;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventSession {

    private EventDefinition currentDefinition;
    private final Set<UUID> participantIds = new LinkedHashSet<>();
    private final Set<UUID> spectatorIds = new LinkedHashSet<>();
    private final Map<EventType, Integer> voteCounts = new LinkedHashMap<>();
    private EventPhase currentPhase;
    private Location waitingHubLocation;
    private Location trophyRoomLocation;
    private EventMap activeMap;
    private String starterName;
    private boolean startedByAdmin;
    private final Map<UUID, String> playerTeams = new LinkedHashMap<>();
    private final List<UUID> rankings = new ArrayList<>();

    public EventSession(EventDefinition definition, EventPhase phase) {
        this.currentDefinition = definition;
        this.currentPhase = phase;
    }

    public EventDefinition definition() {
        return currentDefinition;
    }

    public void definition(EventDefinition definition) {
        this.currentDefinition = definition;
    }

    public Set<UUID> participants() {
        return participantIds;
    }

    public Set<UUID> spectators() {
        return spectatorIds;
    }

    public Map<EventType, Integer> votes() {
        return voteCounts;
    }

    public EventPhase phase() {
        return currentPhase;
    }

    public void phase(EventPhase phase) {
        this.currentPhase = phase;
    }

    public Location waitingHub() {
        return waitingHubLocation;
    }

    public void waitingHub(Location waitingHub) {
        this.waitingHubLocation = waitingHub;
    }

    public Location trophyRoom() {
        return trophyRoomLocation;
    }

    public void trophyRoom(Location trophyRoom) {
        this.trophyRoomLocation = trophyRoom;
    }

    public EventMap selectedMap() {
        return activeMap;
    }

    public void selectedMap(EventMap selectedMap) {
        this.activeMap = selectedMap;
    }

    public String startedBy() {
        return starterName;
    }

    public void startedBy(String startedBy) {
        this.starterName = startedBy;
    }

    public boolean adminStarted() {
        return startedByAdmin;
    }

    public void adminStarted(boolean adminStarted) {
        this.startedByAdmin = adminStarted;
    }

    public Map<UUID, String> teams() {
        return playerTeams;
    }

    public List<UUID> finalRankings() {
        return rankings;
    }
}
