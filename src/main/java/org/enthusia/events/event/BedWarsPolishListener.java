package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Small BedWars presentation fixes kept separate from the main gameplay listener.
 */
public final class BedWarsPolishListener implements Listener {

    private final EventManager eventManager;

    public BedWarsPolishListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedWarsBlockPlace(BlockPlaceEvent event) {
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }

        Material placed = event.getBlockPlaced().getType();
        Material teamMaterial = teamBuildingMaterial(placed, eventManager.teamFor(event.getPlayer().getUniqueId()));
        if (teamMaterial != null && teamMaterial != placed) {
            event.getBlockPlaced().setType(teamMaterial, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedWarsBedBreak(BlockBreakEvent event) {
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!event.getBlock().getType().name().endsWith("_BED")) {
            return;
        }

        Set<UUID> listeners = new LinkedHashSet<>(session.participants());
        listeners.addAll(session.spectators());
        for (UUID uuid : listeners) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
            }
        }
    }

    private EventSession activeBedWarsSession() {
        EventSession session = eventManager.session();
        if (session == null
                || session.phase() != EventPhase.ACTIVE
                || session.definition() == null
                || session.definition().type() != EventType.BEDWARS) {
            return null;
        }
        return session;
    }

    private Material teamBuildingMaterial(Material placed, String team) {
        if (placed == Material.TERRACOTTA || placed.name().endsWith("_TERRACOTTA")) {
            return teamTerracotta(team);
        }
        if (placed.name().endsWith("_CONCRETE")) {
            return teamConcrete(team);
        }
        return null;
    }

    private Material teamTerracotta(String team) {
        return switch (normalizedTeam(team)) {
            case "1", "red" -> Material.RED_TERRACOTTA;
            case "2", "blue" -> Material.BLUE_TERRACOTTA;
            case "3", "green" -> Material.GREEN_TERRACOTTA;
            case "4", "yellow" -> Material.YELLOW_TERRACOTTA;
            case "5", "orange" -> Material.ORANGE_TERRACOTTA;
            case "6", "purple" -> Material.PURPLE_TERRACOTTA;
            case "7", "cyan" -> Material.CYAN_TERRACOTTA;
            default -> Material.WHITE_TERRACOTTA;
        };
    }

    private Material teamConcrete(String team) {
        return switch (normalizedTeam(team)) {
            case "1", "red" -> Material.RED_CONCRETE;
            case "2", "blue" -> Material.BLUE_CONCRETE;
            case "3", "green" -> Material.GREEN_CONCRETE;
            case "4", "yellow" -> Material.YELLOW_CONCRETE;
            case "5", "orange" -> Material.ORANGE_CONCRETE;
            case "6", "purple" -> Material.PURPLE_CONCRETE;
            case "7", "cyan" -> Material.CYAN_CONCRETE;
            default -> Material.WHITE_CONCRETE;
        };
    }

    private String normalizedTeam(String team) {
        return team == null ? "" : team.toLowerCase(Locale.ROOT);
    }
}
