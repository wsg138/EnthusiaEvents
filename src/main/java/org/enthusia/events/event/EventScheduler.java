package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.time.LocalDateTime;
import java.util.List;

@SuppressWarnings("PMD.NullAssignment")
public final class EventScheduler {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private BukkitTask task;

    public EventScheduler(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void restart() {
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        if (now.getMinute() == 0 && now.getSecond() == plugin.getConfig().getInt("schedule.hourly-vote-second", 0)) {
            eventManager.startScheduledVote();
        }
        List<Integer> chatMinutes = plugin.getConfig().getIntegerList("schedule.chat-event-minutes");
        if (chatMinutes.contains(now.getMinute()) && now.getSecond() == 0) {
            plugin.getLogger().fine("Chat event hook triggered at " + now);
        }
    }
}
