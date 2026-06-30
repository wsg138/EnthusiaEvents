package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class BoatRaceService implements Listener {

    private static final String BOAT_TAG = "enthusia_boat_race_boat";
    private static final String BOAT_METADATA = "enthusia_boat_race_boat";
    private static final String HORSE_TAG = "enthusia_horse_race_horse";
    private static final String HORSE_METADATA = "enthusia_horse_race_horse";

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final Map<UUID, UUID> playerBoats = new HashMap<>();
    private final Map<UUID, UUID> playerHorses = new HashMap<>();
    private final Map<UUID, Integer> boatCheckpointProgress = new HashMap<>();
    private final Map<String, BlockState> releaseWallOriginals = new LinkedHashMap<>();

    public BoatRaceService(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    public void preparePreStart(EventMap map) {
        CuboidRegion wall = map == null ? null : map.areas().get("release-wall");
        if (wall == null) {
            return;
        }
        fillWall(wall, true);
    }

    public void releaseWall(EventMap map) {
        CuboidRegion wall = map == null ? null : map.areas().get("release-wall");
        if (wall == null) {
            return;
        }
        fillWall(wall, false);
    }

    public void mountPlayer(Player player, Location spawn) {
        if (player == null || spawn == null || spawn.getWorld() == null || !player.isOnline()) {
            return;
        }
        cleanupPlayer(player.getUniqueId());
        Entity entity = spawn.getWorld().spawnEntity(spawn, EntityType.OAK_BOAT);
        if (!(entity instanceof Boat boat)) {
            entity.remove();
            plugin.getLogger().warning("Could not spawn Boat Race boat at " + spawn.getWorld().getName() + " "
                    + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ() + ".");
            return;
        }
        boat.addScoreboardTag(BOAT_TAG);
        boat.setMetadata(BOAT_METADATA, new FixedMetadataValue(plugin, true));
        boat.setInvulnerable(true);
        boat.setPersistent(false);
        boat.setSilent(false);
        boat.setVelocity(new Vector(0, 0, 0));
        boat.setRotation(spawn.getYaw(), 0.0F);
        boat.addPassenger(player);
        playerBoats.put(player.getUniqueId(), boat.getUniqueId());
    }

    public void mountHorse(Player player, Location spawn) {
        if (player == null || spawn == null || spawn.getWorld() == null || !player.isOnline()) {
            return;
        }
        cleanupPlayer(player.getUniqueId());
        Horse horse = spawn.getWorld().spawn(spawn, Horse.class, spawned -> {
            spawned.addScoreboardTag(HORSE_TAG);
            spawned.setMetadata(HORSE_METADATA, new FixedMetadataValue(plugin, true));
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setTamed(true);
            spawned.setAdult();
            spawned.setSilent(false);
            spawned.setCollidable(false);
            spawned.setRotation(spawn.getYaw(), 0.0F);
            if (spawned.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                spawned.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3375D);
            }
            if (spawned.getAttribute(Attribute.JUMP_STRENGTH) != null) {
                spawned.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.75D);
            }
        });
        horse.getInventory().setSaddle(new org.bukkit.inventory.ItemStack(Material.SADDLE));
        horse.addPassenger(player);
        playerHorses.put(player.getUniqueId(), horse.getUniqueId());
    }

    public void cleanupPlayer(UUID uuid) {
        boatCheckpointProgress.remove(uuid);
        UUID boatId = playerBoats.remove(uuid);
        if (boatId != null) {
            Entity boat = Bukkit.getEntity(boatId);
            if (boat != null) {
                boat.remove();
            }
        }
        UUID horseId = playerHorses.remove(uuid);
        if (horseId != null) {
            Entity horse = Bukkit.getEntity(horseId);
            if (horse != null) {
                horse.remove();
            }
        }
    }

    public void cleanupAll() {
        for (UUID uuid : List.copyOf(playerBoats.keySet())) {
            cleanupPlayer(uuid);
        }
        for (UUID uuid : List.copyOf(playerHorses.keySet())) {
            cleanupPlayer(uuid);
        }
        restoreReleaseWall();
    }

    public boolean isRaceBoat(Entity entity) {
        return entity != null && (entity.getScoreboardTags().contains(BOAT_TAG) || entity.hasMetadata(BOAT_METADATA));
    }

    public boolean isRaceHorse(Entity entity) {
        return entity != null && (entity.getScoreboardTags().contains(HORSE_TAG) || entity.hasMetadata(HORSE_METADATA));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if ((isRaceBoat(event.getVehicle()) || isRaceHorse(event.getVehicle())) && event.getExited() instanceof Player player && isRaceMountParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && (isRaceBoat(event.getDismounted()) || isRaceHorse(event.getDismounted())) && isRaceMountParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (isRaceBoat(event.getEntity()) || isRaceHorse(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (isRaceBoat(event.getVehicle()) || isRaceHorse(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (isRaceBoat(event.getVehicle()) || isRaceHorse(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCollision(VehicleEntityCollisionEvent event) {
        if (isRaceBoat(event.getVehicle()) || isRaceHorse(event.getVehicle())
                || isRaceBoat(event.getEntity()) || isRaceHorse(event.getEntity())) {
            event.setCancelled(true);
            event.setCollisionCancelled(true);
            event.setPickupCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!isRaceBoat(event.getVehicle())) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.BOAT_RACE) {
            return;
        }
        Player player = event.getVehicle().getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
        if (player == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        Location location = event.getTo();
        recordBoatCheckpoint(player, map, location);
        boolean finished = map.checkpoints().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).startsWith("finish"))
                .map(Map.Entry::getValue)
                .anyMatch(finish -> sameBlock(finish, location));
        if (finished) {
            if (!completedBoatCheckpoints(player, map)) {
                player.sendActionBar(org.bukkit.ChatColor.RED + "Complete every checkpoint before finishing.");
                return;
            }
            eventManager.finishParticipant(player);
        }
    }

    private void recordBoatCheckpoint(Player player, EventMap map, Location location) {
        map.checkpoints().entrySet().stream()
                .filter(entry -> isRaceCheckpoint(entry.getKey()))
                .filter(entry -> sameBlock(entry.getValue(), location))
                .findFirst()
                .ifPresent(entry -> boatCheckpointProgress.merge(player.getUniqueId(), checkpointOrder(entry.getKey()), Math::max));
    }

    private boolean completedBoatCheckpoints(Player player, EventMap map) {
        int required = map.checkpoints().keySet().stream()
                .filter(this::isRaceCheckpoint)
                .mapToInt(this::checkpointOrder)
                .max()
                .orElse(0);
        return required <= 0 || boatCheckpointProgress.getOrDefault(player.getUniqueId(), 0) >= required;
    }

    private boolean isRaceCheckpoint(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("checkpoint") || lower.startsWith("cp");
    }

    private int checkpointOrder(String key) {
        String digits = key.toLowerCase(Locale.ROOT).replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return 1;
        }
        return Integer.parseInt(digits);
    }

    private boolean isRaceMountParticipant(Player player) {
        EventSession session = eventManager.session();
        return session != null
                && (session.definition().type() == EventType.BOAT_RACE || session.definition().type() == EventType.HORSE_RACE)
                && session.participants().contains(player.getUniqueId());
    }

    private boolean sameWorld(Location left, Location right) {
        return left != null && right != null && left.getWorld() != null && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID());
    }

    private boolean sameBlock(Location left, Location right) {
        return sameWorld(left, right)
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private void fillWall(CuboidRegion area, boolean checkered) {
        World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(area.worldName()));
        }
        if (world == null) {
            plugin.getLogger().warning("Skipping release wall update because world " + area.worldName() + " is not loaded.");
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > plugin.getConfig().getLong("boat-race.max-release-wall-blocks", 10_000L)) {
            plugin.getLogger().warning("Skipping oversized Boat Race release wall update (" + blocks + " blocks).");
            return;
        }
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ).load(true);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    releaseWallOriginals.putIfAbsent(blockKey(block.getLocation()), block.getState());
                    if (checkered) {
                        block.setType(((x + y + z) & 1) == 0 ? Material.WHITE_STAINED_GLASS : Material.BLACK_STAINED_GLASS, false);
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void restoreReleaseWall() {
        List<BlockState> states = releaseWallOriginals.values().stream().toList().reversed();
        releaseWallOriginals.clear();
        for (BlockState state : states) {
            state.update(true, false);
        }
    }

    private String blockKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":"
                + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
