package org.enthusia.events.stats;

import org.enthusia.events.event.EventType;

import java.util.EnumMap;
import java.util.Map;

public final class PlayerEventStats {

    private int eventsPlayed;
    private int wins;
    private int losses;
    private int winStreak;
    private int bestStreak;
    private final Map<EventType, Integer> playedByEvent = new EnumMap<>(EventType.class);
    private final Map<EventType, Integer> winsByEvent = new EnumMap<>(EventType.class);
    private final Map<EventType, Integer> lossesByEvent = new EnumMap<>(EventType.class);

    public int eventsPlayed() {
        return eventsPlayed;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int winStreak() {
        return winStreak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public Map<EventType, Integer> playedByEvent() {
        return playedByEvent;
    }

    public Map<EventType, Integer> winsByEvent() {
        return winsByEvent;
    }

    public Map<EventType, Integer> lossesByEvent() {
        return lossesByEvent;
    }

    public void recordParticipation(EventType type) {
        eventsPlayed++;
        playedByEvent.merge(type, 1, Integer::sum);
    }

    public void recordWin(EventType type) {
        wins++;
        winStreak++;
        bestStreak = Math.max(bestStreak, winStreak);
        winsByEvent.merge(type, 1, Integer::sum);
    }

    public void recordLoss(EventType type) {
        losses++;
        winStreak = 0;
        lossesByEvent.merge(type, 1, Integer::sum);
    }

    public void loadTotals(int eventsPlayed, int wins, int losses, int winStreak, int bestStreak) {
        this.eventsPlayed = eventsPlayed;
        this.wins = wins;
        this.losses = losses;
        this.winStreak = winStreak;
        this.bestStreak = bestStreak;
    }

    public void loadPerEvent(EventType type, int played, int wins, int losses) {
        if (played > 0) {
            playedByEvent.put(type, played);
        }
        if (wins > 0) {
            winsByEvent.put(type, wins);
        }
        if (losses > 0) {
            lossesByEvent.put(type, losses);
        }
    }
}
