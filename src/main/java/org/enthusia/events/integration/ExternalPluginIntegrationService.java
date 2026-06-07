package org.enthusia.events.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.projectiles.ProjectileSource;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ExternalPluginIntegrationService implements Listener {

    private static final String COMBAT_TAG_EVENT =
            "com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent";
    private static final String BOUNTY_CLAIM_EVENT =
            "me.jadenp.notbounties.bounty_events.BountyClaimEvent";

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;

    public ExternalPluginIntegrationService(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerOptionalCancellation(COMBAT_TAG_EVENT, "getPlayer");
        registerOptionalCancellation(BOUNTY_CLAIM_EVENT, "getKiller");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void restoreEventPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        EventSession session = eventManager.session();
        if (attacker == null || session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (session.participants().contains(attacker.getUniqueId())
                && session.participants().contains(victim.getUniqueId())) {
            event.setCancelled(false);
        }
    }

    private void registerOptionalCancellation(String eventClassName, String playerGetter) {
        try {
            Class<?> rawClass = Class.forName(eventClassName);
            if (!Event.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().warning("Optional integration event is not a Bukkit event: " + eventClassName);
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getter = rawClass.getMethod(playerGetter);
            EventExecutor executor = (listener, event) -> cancelForEventPlayer(event, getter);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.HIGHEST,
                    executor,
                    plugin,
                    false
            );
            plugin.getLogger().info("Enabled integration for " + eventClassName + ".");
        } catch (ClassNotFoundException ignored) {
            // The optional plugin is not installed.
        } catch (NoSuchMethodException exception) {
            plugin.getLogger().warning("Optional integration API changed for " + eventClassName + ": "
                    + exception.getMessage());
        }
    }

    private void cancelForEventPlayer(Event event, Method getter) {
        if (!(event instanceof Cancellable cancellable)) {
            return;
        }
        try {
            Object value = getter.invoke(event);
            if (value instanceof Player player && eventManager.isEventPlayer(player.getUniqueId())) {
                cancellable.setCancelled(true);
            }
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().warning("Could not process optional plugin event "
                    + event.getEventName() + ": " + exception.getMessage());
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
