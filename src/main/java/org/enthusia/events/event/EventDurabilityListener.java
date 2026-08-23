package org.enthusia.events.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * Prevents temporary event equipment from losing durability while a player is in an event.
 * Normal equipment durability outside Enthusia events is unaffected.
 */
public final class EventDurabilityListener implements Listener {

    private final EventManager eventManager;

    public EventDurabilityListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (eventManager.isEventPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
