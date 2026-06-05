package org.enthusia.events.stats;

import org.enthusia.events.event.EventType;

import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class PlayerEventStats {

    private int totalEventsPlayed;
    private int totalWins;
    private int totalLosses;
    private int currentWinStreak;
    private int highestWinStreak;
    private final Map<EventType, Integer> eventPlayCounts = new EnumMap<>(EventType.class);
    private final Map<EventType, Integer> eventWinCounts = new EnumMap<>(EventType.class);
    private final Map<EventType, Integer> eventLossCounts = new EnumMap<>(EventType.class);

    public int eventsPlayed() {
        return totalEventsPlayed;
    }

    public int wins() {
        return totalWins;
    }

    public int losses() {
        return totalLosses;
    }

    public int winStreak() {
        return currentWinStreak;
    }

    public int bestStreak() {
        return highestWinStreak;
    }

    public Map<EventType, Integer> playedByEvent() {
        return eventPlayCounts;
    }

    public Map<EventType, Integer> winsByEvent() {
        return eventWinCounts;
    }

    public Map<EventType, Integer> lossesByEvent() {
        return eventLossCounts;
    }

    public void recordParticipation(EventType type) {
        totalEventsPlayed++;
        eventPlayCounts.merge(type, 1, Integer::sum);
    }

    public void recordWin(EventType type) {
        totalWins++;
        currentWinStreak++;
        highestWinStreak = Math.max(highestWinStreak, currentWinStreak);
        eventWinCounts.merge(type, 1, Integer::sum);
    }

    public void recordLoss(EventType type) {
        totalLosses++;
        currentWinStreak = 0;
        eventLossCounts.merge(type, 1, Integer::sum);
    }

    public void loadTotals(int eventsPlayed, int wins, int losses, int winStreak, int bestStreak) {
        this.totalEventsPlayed = eventsPlayed;
        this.totalWins = wins;
        this.totalLosses = losses;
        this.currentWinStreak = winStreak;
        this.highestWinStreak = bestStreak;
    }

    public void loadPerEvent(EventType type, int played, int wins, int losses) {
        if (played > 0) {
            eventPlayCounts.put(type, played);
        }
        if (wins > 0) {
            eventWinCounts.put(type, wins);
        }
        if (losses > 0) {
            eventLossCounts.put(type, losses);
        }
    }
}
