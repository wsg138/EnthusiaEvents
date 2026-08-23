package org.enthusia.events.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hard containment for jailed Capture Players participants.
 *
 * The main gameplay listener already teleports jailed players back when it notices them
 * outside the configured jail. This listener adds a final movement guard that remembers
 * the exact jail region the prisoner was placed into and prevents crossing its boundary.
 */
public final class CapturePlayersJailGuardListener implements Listener {

    private final EventManager eventManager;
    private final Map<UUID, CuboidRegion> jailedRegions = new HashMap<>();

    public CapturePlayersJailGuardListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        EventSession session = activeCapturePlayersSession();
        if (session == null || !session.participants().contains(player.getUniqueId())) {
            jailedRegions.remove(player.getUniqueId());
            return;
        }

        // Captured/rescue-carried players are Adventure passengers. Do not fight the
        // existing carry mechanic; confinement resumes automatically if they return to jail.
        if (player.isInsideVehicle()) {
            return;
        }

        // A free/rescued Capture Players participant is restored to Survival.
        if (player.getGameMode() != GameMode.ADVENTURE) {
            jailedRegions.remove(player.getUniqueId());
            return;
        }

        EventMap map = session.selectedMap();
        if (map == null || event.getTo() == null) {
            return;
        }

        CuboidRegion jail = jailedRegions.get(player.getUniqueId());
        if (jail == null) {
            jail = findContainingJail(map, event.getFrom());
            if (jail == null) {
                jail = findContainingJail(map, player.getLocation());
            }
            if (jail == null) {
                return;
            }
            jailedRegions.put(player.getUniqueId(), jail);
        }

        if (jail.contains(event.getTo())) {
            return;
        }

        Location safe = jail.contains(event.getFrom()) ? event.getFrom().clone() : center(jail);
        event.setTo(safe);
        player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        player.setFallDistance(0.0F);
    }

    private EventSession activeCapturePlayersSession() {
        EventSession session = eventManager.session();
        if (session == null
                || session.phase() != EventPhase.ACTIVE
                || session.definition() == null
                || session.definition().type() != EventType.CAPTURE_PLAYERS) {
            jailedRegions.clear();
            return null;
        }
        return session;
    }

    private CuboidRegion findContainingJail(EventMap map, Location location) {
        if (location == null) {
            return null;
        }
        return map.areas().entrySet().stream()
                .filter(entry -> isJailArea(entry.getKey()))
                .sorted(Comparator.comparing(entry -> "jail-zone".equalsIgnoreCase(entry.getKey())))
                .map(Map.Entry::getValue)
                .filter(region -> region != null && region.contains(location))
                .findFirst()
                .orElse(null);
    }

    private boolean isJailArea(String key) {
        return key != null && (key.equalsIgnoreCase("jail-zone") || key.toLowerCase(java.util.Locale.ROOT).startsWith("jail-"));
    }

    private Location center(CuboidRegion region) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(region.worldName());
        if (world == null) {
            return new Location(null,
                    (region.minX() + region.maxX() + 1.0D) / 2.0D,
                    region.minY() + 0.1D,
                    (region.minZ() + region.maxZ() + 1.0D) / 2.0D);
        }
        return new Location(world,
                (region.minX() + region.maxX() + 1.0D) / 2.0D,
                region.minY() + 0.1D,
                (region.minZ() + region.maxZ() + 1.0D) / 2.0D);
    }
}
