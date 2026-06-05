package org.enthusia.events.util;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class TeleportService {

    private TeleportService() {
    }

    public static CompletableFuture<Boolean> teleport(JavaPlugin plugin, Player player, Location target, String reason) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable task = () -> {
            if (!player.isOnline()) {
                result.complete(false);
                return;
            }
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            if (target == null) {
                plugin.getLogger().warning("Teleport failed for " + player.getName() + " (" + reason + "): target location is not set.");
                result.complete(false);
                return;
            }
            Location destination = target.clone();
            World world = destination.getWorld();
            if (world == null) {
                plugin.getLogger().warning("Teleport failed for " + player.getName() + " (" + reason + "): target world is not loaded.");
                result.complete(false);
                return;
            }
            String worldName = world.getName();
            World loadedWorld = Bukkit.getWorld(worldName);
            if (loadedWorld == null) {
                loadedWorld = Bukkit.createWorld(new WorldCreator(worldName));
            }
            if (loadedWorld == null) {
                plugin.getLogger().warning("Teleport failed for " + player.getName() + " (" + reason + "): could not load world " + worldName + ".");
                result.complete(false);
                return;
            }
            destination.setWorld(loadedWorld);
            int chunkX = destination.getBlockX() >> 4;
            int chunkZ = destination.getBlockZ() >> 4;
            Chunk chunk = loadedWorld.getChunkAt(chunkX, chunkZ);
            if (!chunk.isLoaded() && !chunk.load(true)) {
                plugin.getLogger().warning("Teleport failed for " + player.getName() + " (" + reason
                        + "): could not load chunk " + chunkX + "," + chunkZ + " in " + worldName + ".");
                result.complete(false);
                return;
            }
            chunk.addPluginChunkTicket(plugin);
            result.whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (chunk.isLoaded()) {
                    chunk.removePluginChunkTicket(plugin);
                }
            }, 200L));
            if (!player.getWorld().getUID().equals(loadedWorld.getUID())) {
                attemptSyncTeleport(plugin, player, destination, reason, result, worldName, chunkX, chunkZ, chunk, 1);
                return;
            }
            player.teleportAsync(destination).whenComplete((success, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().warning("Teleport failed for " + player.getName() + " (" + reason + "): " + throwable.getMessage());
                    result.complete(false);
                    return;
                }
                if (!Boolean.TRUE.equals(success)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        attemptSyncTeleport(plugin, player, destination, reason, result, worldName, chunkX, chunkZ, chunk, 1);
                    });
                    return;
                }
                result.complete(true);
            });
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
        return result;
    }

    private static void attemptSyncTeleport(JavaPlugin plugin, Player player, Location destination, String reason,
                                            CompletableFuture<Boolean> result, String worldName, int chunkX, int chunkZ,
                                            Chunk chunk, int attempt) {
        if (!player.isOnline()) {
            result.complete(false);
            return;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        player.setFallDistance(0.0F);
        boolean success = player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!success) {
            success = player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND);
        }
        if (success) {
            result.complete(true);
            return;
        }
        if (attempt < 4) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    attemptSyncTeleport(plugin, player, destination, reason, result, worldName, chunkX, chunkZ, chunk, attempt + 1), 5L);
            return;
        }
        plugin.getLogger().warning("Bukkit teleport failed for " + player.getName() + " (" + reason + ") to "
                + worldName + " " + destination.getBlockX() + "," + destination.getBlockY() + "," + destination.getBlockZ()
                + " after " + attempt + " sync attempts. Trying vanilla command fallback.");
        if (attemptCommandTeleport(plugin, player, destination)) {
            plugin.getLogger().warning("Vanilla command teleport fallback succeeded for " + player.getName() + " (" + reason + ").");
            result.complete(true);
            return;
        }
        plugin.getLogger().warning("Teleport failed for " + player.getName() + " (" + reason + ") to "
                + worldName + " " + destination.getBlockX() + "," + destination.getBlockY() + "," + destination.getBlockZ()
                + " after " + attempt + " sync attempts. Chunk " + chunkX + "," + chunkZ + " loaded=" + chunk.isLoaded() + ".");
        result.complete(false);
    }

    private static boolean attemptCommandTeleport(JavaPlugin plugin, Player player, Location destination) {
        World world = destination.getWorld();
        if (world == null || !player.isOnline()) {
            return false;
        }
        String command = "execute in " + world.getKey().asString()
                + " run tp " + player.getName()
                + " " + coordinate(destination.getX())
                + " " + coordinate(destination.getY())
                + " " + coordinate(destination.getZ())
                + " " + coordinate(destination.getYaw())
                + " " + coordinate(destination.getPitch());
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            plugin.getLogger().warning("Fallback teleport command was not dispatched for " + player.getName() + ": " + command);
            return false;
        }
        Location after = player.getLocation();
        return after.getWorld() != null
                && after.getWorld().getUID().equals(world.getUID())
                && after.distanceSquared(destination) <= 9.0D;
    }

    private static String coordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
