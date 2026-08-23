package org.enthusia.events.event;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protects temporary event equipment and prevents event-state inventory from escaping
 * into persistent systems while a player's pre-event snapshot is still pending restore.
 */
public final class EventDurabilityListener implements Listener {

    private static final long MANUAL_SPECTATE_WINDOW_MILLIS = 2_000L;

    private final EventManager eventManager;
    private final Map<UUID, Long> manualSpectateRequests = new ConcurrentHashMap<>();

    public EventDurabilityListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (eventManager.isEventPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() <= 1) {
            return;
        }
        String commandLine = raw.substring(1).trim();
        if (commandLine.isEmpty()) {
            return;
        }
        String[] parts = commandLine.split("\\s+");
        String root = stripNamespace(parts[0]);
        String subcommand = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";

        if ((root.equals("event") || root.equals("ee")) && subcommand.equals("spectate")) {
            manualSpectateRequests.put(event.getPlayer().getUniqueId(),
                    System.currentTimeMillis() + MANUAL_SPECTATE_WINDOW_MILLIS);
        }

        if (!eventManager.isEventPlayer(event.getPlayer().getUniqueId())) {
            return;
        }

        boolean blockedPersistentStorage = root.equals("g")
                || (root.equals("guild") && subcommand.equals("vault"));
        if (blockedPersistentStorage) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED
                    + "That command is disabled while you are in an event.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.SPECTATOR) {
            return;
        }
        Player player = event.getPlayer();
        EventSession session = eventManager.session();
        if (session == null || !eventManager.isEventPlayer(player.getUniqueId())) {
            return;
        }

        // A spectator must never retain temporary event inventory. The player's real inventory
        // is already protected by the snapshot and will be restored when they leave the event.
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();

        Long manualUntil = manualSpectateRequests.remove(player.getUniqueId());
        boolean manualSpectate = manualUntil != null && manualUntil >= System.currentTimeMillis();
        if (!manualSpectate
                && session.phase() == EventPhase.ACTIVE
                && session.definition().type() == EventType.RED_LIGHT_GREEN_LIGHT
                && session.spectators().contains(player.getUniqueId())) {
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED
                    + player.getName() + " was eliminated for moving on red light.");
        }
    }

    private String stripNamespace(String root) {
        String normalized = root.toLowerCase(Locale.ROOT);
        int colon = normalized.lastIndexOf(':');
        return colon >= 0 ? normalized.substring(colon + 1) : normalized;
    }
}
