package org.enthusia.events.event;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.util.RayTraceResult;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.TeleportService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({
        "PMD.UseConcurrentHashMap",
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidFieldNameMatchingMethodName",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.NPathComplexity",
        "PMD.NullAssignment"
})
public final class EventGameplayListener implements Listener {

    private static final List<Material> BLOCK_PARTY_COLORS = List.of(
            Material.WHITE_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE,
            Material.PINK_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.RED_CONCRETE,
            Material.BLACK_CONCRETE
    );

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final Map<UUID, String> raceCheckpoint = new HashMap<>();
    private final Map<UUID, Integer> raceCheckpointOrder = new HashMap<>();
    private final Map<UUID, Location> raceSafeLocation = new HashMap<>();
    private final Map<UUID, String> ctfCarriers = new HashMap<>();
    private final Map<String, Integer> ctfScores = new HashMap<>();
    private final Map<String, BlockState> ctfOriginalFlagBlocks = new HashMap<>();
    private final Map<String, Location> ctfRenderedFlagBlocks = new HashMap<>();
    private final Map<String, Location> ctfDroppedFlags = new HashMap<>();
    private final Map<String, Integer> ctfCaptureProgress = new HashMap<>();
    private final Map<UUID, String> capturedPlayers = new HashMap<>();
    private final Map<String, Integer> capturePlayerScores = new HashMap<>();
    private final Map<UUID, Integer> quakeScores = new HashMap<>();
    private final Map<UUID, BukkitTask> quakeRespawns = new HashMap<>();
    private final Map<UUID, BukkitTask> ctfRespawns = new HashMap<>();
    private final List<String> brokenBedTeams = new ArrayList<>();
    private final List<UUID> finishOrder = new ArrayList<>();
    private final Map<UUID, Location> redLightLastSafeLocation = new HashMap<>();
    private EventSession trackedSession;
    private BukkitTask tickTask;
    private BukkitTask blockPartyTask;
    private BukkitTask ctfParticleTask;
    private UUID hotPotatoHolder;
    private int hotPotatoSeconds;
    private boolean greenLight;
    private int redLightSeconds;
    private Material blockPartyTarget;
    private int blockPartySeconds;
    private int blockPartyTicksRemaining;
    private int blockPartyRoundTicks;
    private int blockPartySoundStage;
    private int blockPartyRound;
    private int blockPartyClearDelay;
    private int bedWarsGeneratorTicks;
    private final Map<UUID, Long> quakeShotCooldowns = new HashMap<>();
    private final Map<UUID, Long> quakeLaunchCooldowns = new HashMap<>();

    public EventGameplayListener(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActiveEvent, 20L, 20L);
        this.ctfParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCaptureTheFlagParticles, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (ctfParticleTask != null) {
            ctfParticleTask.cancel();
            ctfParticleTask = null;
        }
        if (blockPartyTask != null) {
            blockPartyTask.cancel();
            blockPartyTask = null;
        }
        quakeRespawns.values().forEach(BukkitTask::cancel);
        quakeRespawns.clear();
        ctfRespawns.values().forEach(BukkitTask::cancel);
        ctfRespawns.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map == null || map.region() == null) {
            return;
        }

        EventType type = session.definition().type();
        if (type == EventType.CAPTURE_THE_FLAG) {
            handleCaptureTheFlagMove(session, map, event.getPlayer(), event.getTo());
            return;
        }
        if (type == EventType.CAPTURE_PLAYERS) {
            handleCapturePlayersMove(event.getPlayer(), map, event.getTo());
            return;
        }
        if (isRaceType(type)) {
            handleRaceMove(session, map, event.getPlayer(), event.getTo());
            return;
        }

        double eliminationY = type == EventType.BLOCK_PARTY ? 65.0D : map.region().minY() - 1.0D;
        if (usesFallElimination(type) && event.getTo() != null && event.getTo().getY() <= eliminationY) {
            String reason = type == EventType.BLOCK_PARTY
                    ? "after " + Math.max(0, blockPartyRound) + " rounds"
                    : "fell out of the arena";
            eventManager.eliminateParticipant(event.getPlayer(), reason);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.HORSE_RACE) {
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
        if (map != null) {
            handleRaceMove(session, map, player, event.getTo());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        EventMap map = session.selectedMap();
        if (map != null && map.region() != null && map.region().contains(event.getBlock().getLocation())
                && (type == EventType.SKYWARS || type == EventType.BEDWARS)) {
            if (type == EventType.BEDWARS && isBedBlock(event.getBlock().getType())) {
                handleBedWarsBedBreak(session, map, event);
            }
            return;
        }
        if (map != null && map.region() != null && map.region().contains(event.getBlock().getLocation())
                && (type == EventType.SPLEEF || type == EventType.SPLEEG)
                && canBreakSpleefBlock(map, event.getBlock())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        EventMap map = session.selectedMap();
        if (map != null && map.region() != null && map.region().contains(event.getBlockPlaced().getLocation())
                && (type == EventType.SKYWARS || type == EventType.BEDWARS)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        if (type == EventType.ELYTRA_RACE
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && event.getItem().getType() == Material.RECOVERY_COMPASS) {
            event.setCancelled(true);
            EventMap map = session.selectedMap();
            if (map != null) {
                respawnRace(event.getPlayer(), map);
            }
            return;
        }
        if (type == EventType.SPLEEG && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (type == EventType.QUAKE && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.GOLDEN_HOE) {
                return;
            }
            fireQuake(event.getPlayer(), session);
            return;
        }
        if (type == EventType.QUAKE && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.GOLDEN_HOE) {
                return;
            }
            launchQuake(event.getPlayer());
            return;
        }
        if (type == EventType.PARKOUR || type == EventType.BLOCK_PARTY || type == EventType.RED_LIGHT_GREEN_LIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.PRESTART || session.definition().type() != EventType.SKYWARS) {
            return;
        }
        if (!session.participants().contains(player.getUniqueId())) {
            return;
        }
        Location location = event.getInventory().getLocation();
        EventMap map = session.selectedMap();
        if (location != null && map != null && map.region() != null && map.region().contains(location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Chests unlock when the event starts.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        if (type == EventType.QUAKE) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.CAPTURE_THE_FLAG) {
            if (!(event instanceof EntityDamageByEntityEvent) && player.getHealth() - event.getFinalDamage() <= 0.0D) {
                event.setCancelled(true);
                EventMap map = session.selectedMap();
                if (map != null) {
                    dropCarriedFlag(player, groundFlagLocation(player.getLocation()));
                    scheduleCaptureTheFlagRespawn(session, map, player, "You will respawn in 5 seconds.");
                }
            }
            return;
        }
        if (type == EventType.PARKOUR || type == EventType.BLOCK_PARTY || type == EventType.RED_LIGHT_GREEN_LIGHT
                || type == EventType.BOAT_RACE || type == EventType.HORSE_RACE || type == EventType.ELYTRA_RACE
                || (type == EventType.CAPTURE_PLAYERS && !(event instanceof EntityDamageByEntityEvent))) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.SUMO_1V1 || type == EventType.SUMO_2V2 || type == EventType.SUMO_FFA
                || type == EventType.KNOCKBACK_FFA) {
            event.setDamage(0.0D);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        EventType type = session.definition().type();
        if (type == EventType.QUAKE && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        Player damager = damagingPlayer(event.getDamager());
        if (type == EventType.CAPTURE_THE_FLAG && event.getEntity() instanceof Player target && damager != null) {
            handleCaptureTheFlagDamage(session, target, damager, event);
            return;
        }
        if (type == EventType.CAPTURE_PLAYERS && event.getEntity() instanceof Player target && damager != null) {
            handleCapturePlayersDamage(session, target, damager, event);
            return;
        }
        if (type == EventType.ONE_IN_THE_CHAMBER && damager != null && event.getDamager() instanceof Arrow) {
            event.setDamage(1000.0D);
            return;
        }
        if (type == EventType.HOT_POTATO && damager != null && event.getEntity() instanceof Player target
                && session.participants().contains(damager.getUniqueId())
                && session.participants().contains(target.getUniqueId())
                && damager.getUniqueId().equals(hotPotatoHolder)) {
            hotPotatoHolder = target.getUniqueId();
            hotPotatoSeconds = 12;
            damager.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You passed the hot potato.");
            target.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You have the hot potato.");
            event.setDamage(0.0D);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = player.getKiller();
        if (session.definition().type() == EventType.ONE_IN_THE_CHAMBER && killer != null && session.participants().contains(killer.getUniqueId())) {
            killer.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.ARROW, 1));
        }
        if (session.definition().type() == EventType.BEDWARS) {
            String team = session.teams().get(player.getUniqueId());
            if (team != null && !brokenBedTeams.contains(team)) {
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Your bed is still alive. You will respawn.");
                return;
            }
        }
        eventManager.eliminateParticipant(player, "died");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        EventSession session = eventManager.session();
        if (session == null) {
            return;
        }
        EventMap map = session.selectedMap();
        if (session.definition().type() == EventType.BEDWARS && session.participants().contains(event.getPlayer().getUniqueId())) {
            Location spawn = bedWarsRespawn(session, map, event.getPlayer());
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getPlayer().setGameMode(GameMode.SURVIVAL);
                event.getPlayer().getInventory().clear();
                event.getPlayer().getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.WOODEN_SWORD));
            });
            return;
        }
        if (!session.spectators().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (map != null && map.spectatorSpawn() != null) {
            event.setRespawnLocation(map.spectatorSpawn());
        }
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().setGameMode(GameMode.SPECTATOR));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.SPLEEG) {
            return;
        }
        if (!(event.getEntity() instanceof Snowball)) {
            return;
        }
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player player) || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        Block block = event.getHitBlock();
        EventMap map = session.selectedMap();
        if (block != null && map != null && map.region() != null && map.region().contains(block.getLocation())
                && canBreakSpleefBlock(map, block)) {
            block.setType(Material.AIR, false);
        }
    }

    public void resetForNewSession() {
        raceCheckpoint.clear();
        raceCheckpointOrder.clear();
        raceSafeLocation.clear();
        ctfCarriers.clear();
        ctfScores.clear();
        restoreCaptureTheFlagBlocks();
        ctfRenderedFlagBlocks.clear();
        ctfDroppedFlags.clear();
        ctfCaptureProgress.clear();
        quakeScores.clear();
        quakeRespawns.values().forEach(BukkitTask::cancel);
        quakeRespawns.clear();
        ctfRespawns.values().forEach(BukkitTask::cancel);
        ctfRespawns.clear();
        capturedPlayers.clear();
        capturePlayerScores.clear();
        brokenBedTeams.clear();
        finishOrder.clear();
        redLightLastSafeLocation.clear();
        hotPotatoHolder = null;
        hotPotatoSeconds = 0;
        greenLight = false;
        redLightSeconds = 0;
        blockPartyTarget = null;
        blockPartySeconds = 0;
        blockPartyTicksRemaining = 0;
        blockPartyRoundTicks = 0;
        blockPartySoundStage = 0;
        blockPartyRound = 0;
        blockPartyClearDelay = 0;
        if (blockPartyTask != null) {
            blockPartyTask.cancel();
            blockPartyTask = null;
        }
        bedWarsGeneratorTicks = 0;
        quakeShotCooldowns.clear();
        quakeLaunchCooldowns.clear();
        trackedSession = null;
    }

    private void tickActiveEvent() {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        switch (session.definition().type()) {
            case HOT_POTATO -> tickHotPotato(session);
            case RED_LIGHT_GREEN_LIGHT -> tickRedLightGreenLight(session);
            case BLOCK_PARTY -> ensureBlockPartyTask();
            case CAPTURE_THE_FLAG -> ensureCaptureTheFlagBlocks(session.selectedMap());
            case BEDWARS -> tickBedWarsGenerators(session);
            default -> {
            }
        }
    }

    private void handleRaceMove(EventSession session, EventMap map, Player player, Location to) {
        if (to == null) {
            return;
        }
        EventType type = session.definition().type();
        Location checkLocation = type == EventType.HORSE_RACE && player.isInsideVehicle() && player.getVehicle() != null
                ? player.getVehicle().getLocation()
                : to;
        if (checkLocation.getWorld() == null || map.worldName() == null || !checkLocation.getWorld().getName().equalsIgnoreCase(map.worldName())) {
            return;
        }
        Location safe = safeLocation(player, map);
        double fallDistance = raceFallDistance(type);
        if (type == EventType.HORSE_RACE && checkLocation.getY() <= 65.0D) {
            respawnRace(player, map);
            return;
        }
        if (sameWorld(safe, checkLocation) && (checkLocation.getY() < safe.getY() - fallDistance || checkLocation.getY() <= player.getWorld().getMinHeight() + 2)) {
            respawnRace(player, map);
            return;
        }

        Map.Entry<String, CuboidRegion> areaCheckpoint = reachedRaceArea(map, checkLocation, type);
        if (areaCheckpoint != null) {
            if (isFinishCheckpoint(areaCheckpoint.getKey())) {
                finishRace(player);
                return;
            }
            recordRaceCheckpoint(player, map, areaCheckpoint.getKey(), center(areaCheckpoint.getValue()));
            return;
        }

        Material marker = checkLocation.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();
        if ((type == EventType.PARKOUR || type == EventType.ELYTRA_RACE) && marker == Material.EMERALD_BLOCK) {
            finishRace(player);
            return;
        }
        if ((type == EventType.PARKOUR || type == EventType.ELYTRA_RACE) && marker == Material.GOLD_BLOCK) {
            String autoCheckpoint = "auto-" + checkLocation.getBlockX() + "-" + checkLocation.getBlockY() + "-" + checkLocation.getBlockZ();
            Map.Entry<String, Location> nearest = nearestCheckpoint(map, checkLocation);
            int order = nearest == null ? checkpointOrder(autoCheckpoint) : checkpointOrder(nearest.getKey());
            int currentBest = raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0);
            if (order > currentBest) {
                recordRaceCheckpoint(player, map, nearest == null ? autoCheckpoint : nearest.getKey(), nearest == null ? checkLocation : nearest.getValue());
            }
            return;
        }

        for (Map.Entry<String, Location> checkpoint : orderedCheckpoints(map)) {
            String key = checkpoint.getKey().toLowerCase(Locale.ROOT);
            boolean reached = isFinishCheckpoint(key)
                    ? sameBlock(checkpoint.getValue(), checkLocation)
                    : sameWorld(checkpoint.getValue(), checkLocation) && checkpoint.getValue().distanceSquared(checkLocation) <= (type == EventType.HORSE_RACE ? 16.0D : 4.0D);
            if (reached) {
                if (key.equals(raceCheckpoint.get(player.getUniqueId()))) {
                    continue;
                }
                if (isFinishCheckpoint(key)) {
                    finishRace(player);
                    return;
                }
                int order = checkpointOrder(checkpoint.getKey());
                int currentBest = raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0);
                if (order <= currentBest) {
                    return;
                }
                recordRaceCheckpoint(player, map, checkpoint.getKey(), checkpoint.getValue());
                return;
            }
        }
    }

    private void recordRaceCheckpoint(Player player, EventMap map, String key, Location checkpointLocation) {
        int order = checkpointOrder(key);
        int currentBest = raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0);
        if (order <= currentBest) {
            return;
        }
        raceCheckpoint.put(player.getUniqueId(), key);
        raceCheckpointOrder.put(player.getUniqueId(), order);
        eventManager.setRuntimeScoreboardValue("checkpoint-" + player.getUniqueId(), checkpointNumber(key).isBlank() ? key : checkpointNumber(key));
        Location respawn = checkpointRespawn(map, key, checkpointLocation);
        if (respawn != null) {
            raceSafeLocation.put(player.getUniqueId(), respawn.clone());
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + checkpointMessage(key));
    }

    private void respawnRace(Player player, EventMap map) {
        Location checkpoint = raceSafeLocation.get(player.getUniqueId());
        String id = raceCheckpoint.get(player.getUniqueId());
        if (checkpoint == null && id != null) {
            checkpoint = checkpointRespawn(map, id, map.checkpoints().get(id));
        }
        if (checkpoint == null && !map.spawns().isEmpty()) {
            checkpoint = map.spawns().values().iterator().next();
        }
        if (checkpoint != null) {
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector(0, 0, 0));
            Location target = checkpoint.clone();
            TeleportService.teleport(plugin, player, target, "race checkpoint respawn").thenAccept(success -> {
                if (success) {
                    eventManager.remountRaceVehicle(player, target);
                }
            });
            String checkpointNumber = checkpointNumber(id);
            if (!checkpointNumber.isBlank()) {
                plugin.messages().send(player, "parkour-respawned", Map.of("checkpoint", checkpointNumber));
            }
        }
    }

    private double raceFallDistance(EventType type) {
        if (type == EventType.HORSE_RACE) {
            return plugin.getConfig().getDouble("horse-race.fall-distance-below-last-safe-point", 96.0D);
        }
        if (type == EventType.BOAT_RACE) {
            return plugin.getConfig().getDouble("boat-race.fall-distance-below-last-safe-point", 96.0D);
        }
        return plugin.getConfig().getDouble("parkour.fall-distance-below-last-safe-point", 8.0D);
    }

    private boolean sameWorld(Location left, Location right) {
        return left != null && right != null
                && left.getWorld() != null && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID());
    }

    private void handleCaptureTheFlagMove(EventSession session, EventMap map, Player player, Location to) {
        if (to == null) {
            return;
        }
        if (map.region() != null && to.getY() < map.region().minY() - 1.0D) {
            dropCarriedFlag(player, groundFlagLocation(player.getLocation()));
            scheduleCaptureTheFlagRespawn(session, map, player, "You will respawn in 5 seconds.");
            return;
        }
        String ownTeam = session.teams().get(player.getUniqueId());
        if (ownTeam == null) {
            ownTeam = nearestTeamByFlag(map, to);
            if (ownTeam == null) {
                ownTeam = "1";
            }
            session.teams().put(player.getUniqueId(), ownTeam);
        }

        String carriedTeam = ctfCarriers.get(player.getUniqueId());
        if (carriedTeam != null && reachedFlagTeam(map, to) != null && reachedFlagTeam(map, to).equals(ownTeam)) {
            captureFlag(session, player, ownTeam, carriedTeam);
        }
    }

    private void handleCaptureTheFlagDamage(EventSession session, Player target, Player damager, EntityDamageByEntityEvent event) {
        if (!session.participants().contains(target.getUniqueId()) || !session.participants().contains(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        String targetTeam = session.teams().get(target.getUniqueId());
        String damagerTeam = session.teams().get(damager.getUniqueId());
        if (targetTeam != null && targetTeam.equals(damagerTeam)) {
            event.setCancelled(true);
            return;
        }
        if (target.getHealth() - event.getFinalDamage() > 0.0D) {
            return;
        }
        event.setCancelled(true);
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        String carriedTeam = ctfCarriers.remove(target.getUniqueId());
        if (carriedTeam != null) {
            ctfDroppedFlags.put(carriedTeam, groundFlagLocation(target.getLocation()));
            target.setGlowing(false);
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            restoreCaptureTheFlagArmor(target);
            target.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You dropped the flag.");
            damager.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + target.getName() + " dropped the "
                    + displayTeam(carriedTeam) + " flag.");
        }
        rewardCaptureTheFlagKill(damager);
        scheduleCaptureTheFlagRespawn(session, map, target, "You will respawn in 5 seconds.");
        damager.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You stopped " + target.getName() + ".");
    }

    private void dropCarriedFlag(Player player, Location location) {
        String carriedTeam = ctfCarriers.remove(player.getUniqueId());
        if (carriedTeam == null) {
            return;
        }
        ctfDroppedFlags.put(carriedTeam, location);
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You dropped the flag.");
    }

    private void rewardCaptureTheFlagKill(Player player) {
        player.getInventory().addItem(
                new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE, 1),
                new org.bukkit.inventory.ItemStack(Material.COOKED_BEEF, 2),
                new org.bukkit.inventory.ItemStack(Material.ARROW, 8)
        );
    }

    private void applyCaptureTheFlagCarrierArmor(Player player, String flagTeam) {
        player.getInventory().setHelmet(new org.bukkit.inventory.ItemStack(flagMaterial(flagTeam)));
        player.getInventory().setBoots(new org.bukkit.inventory.ItemStack(Material.DIAMOND_BOOTS));
    }

    private void restoreCaptureTheFlagArmor(Player player) {
        player.getInventory().setHelmet(new org.bukkit.inventory.ItemStack(Material.CHAINMAIL_HELMET));
        player.getInventory().setBoots(new org.bukkit.inventory.ItemStack(Material.CHAINMAIL_BOOTS));
    }

    private void captureFlag(EventSession session, Player player, String ownTeam, String carriedTeam) {
        if (ctfCarriers.containsValue(ownTeam) || ctfDroppedFlags.containsKey(ownTeam)) {
            sendActionBar(player, ChatColor.RED + "Your flag must be home to capture");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            return;
        }
        ctfCarriers.remove(player.getUniqueId());
        ctfDroppedFlags.remove(carriedTeam);
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        int captures = ctfScores.merge(ownTeam, 1, Integer::sum);
        eventManager.setRuntimeScoreboardValue("ctf-score-" + ownTeam.toLowerCase(Locale.ROOT), String.valueOf(captures));
        playForParticipants(session, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.1F);
        messageTeam(session, carriedTeam, ChatColor.GOLD + "[Events] " + ChatColor.RED + "Your flag was captured by " + player.getName() + ".");
        playForTeam(session, carriedTeam, Sound.ENTITY_WITHER_SPAWN, 0.45F, 1.35F);
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + player.getName()
                + " captured the " + displayTeam(carriedTeam) + " flag for " + displayTeam(ownTeam)
                + " (" + captures + " of 3).");
        if (captures >= 3) {
            List<UUID> winners = session.teams().entrySet().stream()
                    .filter(entry -> ownTeam.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .filter(session.participants()::contains)
                    .toList();
            eventManager.endActiveEvent(winners.isEmpty() ? List.of(player.getUniqueId()) : winners);
            return;
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Flag captured.");
    }

    private void tickCaptureTheFlag(EventSession session) {
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        ensureCaptureTheFlagBlocks(map);
        for (UUID uuid : List.copyOf(session.participants())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (ctfRespawns.containsKey(player.getUniqueId())) {
                continue;
            }
            String ownTeam = session.teams().getOrDefault(player.getUniqueId(), nearestTeamByFlag(map, player.getLocation()));
            if (ownTeam == null) {
                continue;
            }
            String carriedTeam = ctfCarriers.get(player.getUniqueId());
            if (carriedTeam != null) {
                player.setGlowing(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, true, false, true));
                applyCaptureTheFlagCarrierArmor(player, carriedTeam);
                String reached = reachedFlagTeam(map, player.getLocation());
                if (ownTeam.equals(reached)) {
                    captureFlag(session, player, ownTeam, carriedTeam);
                }
                continue;
            }
            processFlagPickup(session, map, player, ownTeam);
        }
    }

    private void processFlagPickup(EventSession session, EventMap map, Player player, String ownTeam) {
        for (String flagTeam : flagTeams(map)) {
            if (ctfCarriers.containsValue(flagTeam)) {
                continue;
            }
            Location location = ctfDroppedFlags.getOrDefault(flagTeam, map.points().get("flag-" + flagTeam));
            if (!insideFlagCaptureBox(location, player.getLocation())) {
                clearCtfProgress(player, flagTeam);
                continue;
            }
            if (contestedFlag(session, location, ownTeam)) {
                clearCtfProgress(player, flagTeam);
                sendActionBar(player, ChatColor.RED + "Flag area contested");
                continue;
            }
            String key = player.getUniqueId() + ":" + flagTeam;
            int progress = ctfCaptureProgress.merge(key, 1, Integer::sum);
            if (flagTeam.equals(ownTeam) && ctfDroppedFlags.containsKey(flagTeam)) {
                sendActionBar(player, captureBar("Returning " + displayTeam(flagTeam) + " flag", progress, 60, ChatColor.GREEN));
                if (progress >= 60) {
                    ctfCaptureProgress.keySet().removeIf(value -> value.endsWith(":" + flagTeam));
                    ctfDroppedFlags.remove(flagTeam);
                    sendActionBar(player, ChatColor.GREEN + "Flag returned");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9F, 1.6F);
                    player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Your flag was returned.");
                    ensureCaptureTheFlagBlocks(map);
                }
                continue;
            }
            if (flagTeam.equals(ownTeam)) {
                clearCtfProgress(player, flagTeam);
                continue;
            }
            sendActionBar(player, captureBar("Capturing " + displayTeam(flagTeam) + " flag", progress, 60, ChatColor.YELLOW));
            if (progress >= 60) {
                ctfCaptureProgress.keySet().removeIf(value -> value.endsWith(":" + flagTeam));
                ctfDroppedFlags.remove(flagTeam);
                ctfCarriers.put(player.getUniqueId(), flagTeam);
                clearRenderedFlagBlock(flagTeam);
                player.setGlowing(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, true, false, true));
                applyCaptureTheFlagCarrierArmor(player, flagTeam);
                sendActionBar(player, ChatColor.GREEN + "Flag captured");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
                messageTeam(session, flagTeam, ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName()
                        + " took your flag.");
                playForTeam(session, flagTeam, Sound.BLOCK_BELL_USE, 0.9F, 1.1F);
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                        + "You picked up the " + displayTeam(flagTeam) + " flag. Return to your flag to capture it.");
                return;
            }
        }
    }

    private String captureBar(String label, int progress, int max, ChatColor filledColor) {
        int segments = 18;
        int filled = Math.max(0, Math.min(segments, (int) Math.round(progress / (double) max * segments)));
        StringBuilder builder = new StringBuilder(ChatColor.GOLD + label + " ");
        builder.append(filledColor);
        for (int i = 0; i < filled; i++) {
            builder.append('|');
        }
        builder.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < segments; i++) {
            builder.append('|');
        }
        return builder.toString();
    }

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
    }

    private void ensureCaptureTheFlagBlocks(EventMap map) {
        if (map == null) {
            return;
        }
        for (String team : flagTeams(map)) {
            if (ctfCarriers.containsValue(team)) {
                clearRenderedFlagBlock(team);
                continue;
            }
            Location location = ctfDroppedFlags.getOrDefault(team, map.points().get("flag-" + team));
            if (location == null || location.getWorld() == null) {
                continue;
            }
            renderFlagBlock(team, location);
        }
    }

    private void tickCaptureTheFlagParticles() {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.CAPTURE_THE_FLAG) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map != null) {
            tickCaptureTheFlag(session);
            drawCaptureTheFlagParticles(map);
        }
    }

    private void drawCaptureTheFlagParticles(EventMap map) {
        for (String team : flagTeams(map)) {
            Location location = ctfDroppedFlags.get(team);
            if (location == null) {
                UUID carrier = ctfCarriers.entrySet().stream()
                        .filter(entry -> team.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                Player player = carrier == null ? null : Bukkit.getPlayer(carrier);
                location = player == null ? centeredFlagParticleLocation(map.points().get("flag-" + team)) : player.getLocation().clone().add(0.0D, 0.25D, 0.0D);
            } else {
                location = centeredFlagParticleLocation(location);
            }
            if (location == null || location.getWorld() == null) {
                continue;
            }
            Particle.DustOptions dust = new Particle.DustOptions(teamColor(team), 1.1F);
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2.0D * i / 16.0D;
                location.getWorld().spawnParticle(Particle.DUST,
                        location.getX() + Math.cos(angle) * 1.35D,
                        location.getY() + 0.25D,
                        location.getZ() + Math.sin(angle) * 1.35D,
                        1, 0, 0, 0, 0, dust);
            }
        }
    }

    private Location centeredFlagParticleLocation(Location location) {
        if (location == null) {
            return null;
        }
        Location centered = location.clone();
        if (Math.abs(centered.getX() - centered.getBlockX()) < 0.001D) {
            centered.add(0.5D, 0.0D, 0.0D);
        }
        if (Math.abs(centered.getZ() - centered.getBlockZ()) < 0.001D) {
            centered.add(0.0D, 0.0D, 0.5D);
        }
        return centered;
    }

    private boolean contestedFlag(EventSession session, Location location, String actingTeam) {
        boolean ally = false;
        boolean enemy = false;
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !insideFlagCaptureBox(location, player.getLocation())) {
                continue;
            }
            String team = session.teams().get(player.getUniqueId());
            if (actingTeam.equals(team)) {
                ally = true;
            } else {
                enemy = true;
            }
        }
        return ally && enemy;
    }

    private boolean insideFlagCaptureBox(Location flag, Location player) {
        return sameWorld(flag, player)
                && Math.abs(flag.getX() - player.getX()) <= 1.5D
                && Math.abs(flag.getY() - player.getY()) <= 2.5D
                && Math.abs(flag.getZ() - player.getZ()) <= 1.5D;
    }

    private List<String> flagTeams(EventMap map) {
        return map.points().keySet().stream()
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith("flag-"))
                .map(key -> key.substring("flag-".length()).toLowerCase(Locale.ROOT))
                .toList();
    }

    private Material flagMaterial(String team) {
        return switch (team) {
            case "1", "red" -> Material.RED_BANNER;
            case "2", "blue" -> Material.BLUE_BANNER;
            case "3", "green" -> Material.GREEN_BANNER;
            case "4", "yellow" -> Material.YELLOW_BANNER;
            case "5", "orange" -> Material.ORANGE_BANNER;
            case "6", "purple" -> Material.PURPLE_BANNER;
            case "7", "cyan" -> Material.CYAN_BANNER;
            default -> Material.WHITE_BANNER;
        };
    }

    private void renderFlagBlock(String team, Location location) {
        Location previous = ctfRenderedFlagBlocks.get(team);
        if (previous != null && !sameBlock(previous, location)) {
            clearRenderedFlagBlock(team);
        }
        Block block = location.getBlock();
        ctfOriginalFlagBlocks.putIfAbsent(blockKey(location), block.getState());
        block.setType(flagMaterial(team), false);
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(yawToBlockFace(location.getYaw()));
            block.setBlockData(rotatable, false);
        }
        ctfRenderedFlagBlocks.put(team, block.getLocation());
    }

    private void clearRenderedFlagBlock(String team) {
        Location previous = ctfRenderedFlagBlocks.remove(team);
        if (previous != null && previous.getWorld() != null) {
            previous.getBlock().setType(Material.AIR, false);
        }
    }

    private Location groundFlagLocation(Location source) {
        Location location = source.clone();
        if (location.getWorld() == null) {
            return location;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = Math.min(location.getWorld().getMaxHeight() - 1, location.getBlockY());
        int minY = location.getWorld().getMinHeight();
        for (int y = startY; y >= minY; y--) {
            Block block = location.getWorld().getBlockAt(x, y, z);
            if (!block.getType().isAir()) {
                Location grounded = block.getLocation().add(0.5D, 1.0D, 0.5D);
                grounded.setYaw(source.getYaw());
                grounded.setPitch(0.0F);
                return grounded;
            }
        }
        return location;
    }

    private boolean sameBlock(Location left, Location right) {
        return sameWorld(left, right)
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private String blockKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":"
                + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private BlockFace yawToBlockFace(float yaw) {
        int index = Math.round(((yaw % 360.0F) + 360.0F) % 360.0F / 22.5F) & 0xF;
        return switch (index) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.SOUTH_SOUTH_WEST;
            case 2 -> BlockFace.SOUTH_WEST;
            case 3 -> BlockFace.WEST_SOUTH_WEST;
            case 4 -> BlockFace.WEST;
            case 5 -> BlockFace.WEST_NORTH_WEST;
            case 6 -> BlockFace.NORTH_WEST;
            case 7 -> BlockFace.NORTH_NORTH_WEST;
            case 8 -> BlockFace.NORTH;
            case 9 -> BlockFace.NORTH_NORTH_EAST;
            case 10 -> BlockFace.NORTH_EAST;
            case 11 -> BlockFace.EAST_NORTH_EAST;
            case 12 -> BlockFace.EAST;
            case 13 -> BlockFace.EAST_SOUTH_EAST;
            case 14 -> BlockFace.SOUTH_EAST;
            case 15 -> BlockFace.SOUTH_SOUTH_EAST;
            default -> BlockFace.SOUTH;
        };
    }

    private String displayTeam(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> "Red";
            case "2", "blue" -> "Blue";
            case "3", "green" -> "Green";
            case "4", "yellow" -> "Yellow";
            case "5", "orange" -> "Orange";
            case "6", "purple" -> "Purple";
            case "7", "cyan" -> "Cyan";
            default -> "Team " + team;
        };
    }

    private Color teamColor(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> Color.RED;
            case "2", "blue" -> Color.BLUE;
            case "3", "green" -> Color.GREEN;
            case "4", "yellow" -> Color.YELLOW;
            case "5", "orange" -> Color.ORANGE;
            case "6", "purple" -> Color.PURPLE;
            case "7", "cyan" -> Color.AQUA;
            default -> Color.WHITE;
        };
    }

    private void clearCtfProgress(Player player, String flagTeam) {
        ctfCaptureProgress.remove(player.getUniqueId() + ":" + flagTeam);
    }

    private void restoreCaptureTheFlagBlocks() {
        for (BlockState state : ctfOriginalFlagBlocks.values().stream().toList().reversed()) {
            state.update(true, false);
        }
        ctfRenderedFlagBlocks.clear();
        ctfOriginalFlagBlocks.clear();
    }

    private void respawnCaptureTheFlagPlayer(EventSession session, EventMap map, Player player, String message) {
        ctfCarriers.remove(player.getUniqueId());
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        String team = session.teams().getOrDefault(player.getUniqueId(), nearestTeamByFlag(map, player.getLocation()));
        Location spawn = team == null ? null : map.spawns().get("team-" + team + "-spawn");
        if (spawn == null && team != null) {
            spawn = map.points().get("flag-" + team);
        }
        if (spawn == null && !map.spawns().isEmpty()) {
            spawn = map.spawns().values().iterator().next();
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        if (spawn != null) {
            TeleportService.teleport(plugin, player, spawn, "capture the flag base respawn");
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY + message);
    }

    private void scheduleCaptureTheFlagRespawn(EventSession session, EventMap map, Player player, String message) {
        if (ctfRespawns.containsKey(player.getUniqueId())) {
            return;
        }
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + message);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ctfRespawns.remove(player.getUniqueId());
            EventSession current = eventManager.session();
            if (current == null || current != session || current.phase() != EventPhase.ACTIVE
                    || !current.participants().contains(player.getUniqueId()) || !player.isOnline()) {
                return;
            }
            player.setGameMode(GameMode.SURVIVAL);
            respawnCaptureTheFlagPlayer(current, map, player, "You respawned.");
        }, 100L);
        ctfRespawns.put(player.getUniqueId(), task);
    }

    private void messageTeam(EventSession session, String team, String message) {
        for (UUID uuid : session.participants()) {
            if (!team.equalsIgnoreCase(session.teams().getOrDefault(uuid, ""))) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void playForTeam(EventSession session, String team, Sound sound, float volume, float pitch) {
        for (UUID uuid : session.participants()) {
            if (!team.equalsIgnoreCase(session.teams().getOrDefault(uuid, ""))) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void handleCapturePlayersMove(Player player, EventMap map, Location to) {
        if (to == null) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null) {
            return;
        }
        String playerTeam = session.teams().getOrDefault(player.getUniqueId(), "1");
        if (capturedPlayers.containsKey(player.getUniqueId())) {
            CuboidRegion jail = teamArea(map, "jail", playerTeam);
            Location jailLocation = jail == null ? center(map.areas().get("jail-zone")) : center(jail);
            if (jailLocation != null && (jail == null || !jail.contains(to))) {
                TeleportService.teleport(plugin, player, jailLocation, "capture players jail return");
            }
            return;
        }
        if (!inArea(map, "free-zone", to)) {
            return;
        }
        for (Map.Entry<UUID, String> entry : List.copyOf(capturedPlayers.entrySet())) {
            if (!entry.getValue().equals(playerTeam)) {
                continue;
            }
            Player prisoner = Bukkit.getPlayer(entry.getKey());
            if (prisoner == null) {
                continue;
            }
            capturedPlayers.remove(entry.getKey());
            respawnCapturePlayer(session, map, prisoner, "You were freed by " + player.getName() + ".");
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You freed " + prisoner.getName() + ".");
        }
    }

    private void handleCapturePlayersDamage(EventSession session, Player target, Player damager, EntityDamageByEntityEvent event) {
        if (!session.participants().contains(target.getUniqueId()) || !session.participants().contains(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        String targetTeam = session.teams().getOrDefault(target.getUniqueId(), "1");
        String damagerTeam = session.teams().getOrDefault(damager.getUniqueId(), "2");
        if (targetTeam.equals(damagerTeam) || capturedPlayers.containsKey(target.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (target.getHealth() - event.getFinalDamage() > 0.0D) {
            event.setDamage(Math.min(event.getDamage(), 2.0D));
            return;
        }
        event.setCancelled(true);
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        capturedPlayers.put(target.getUniqueId(), targetTeam);
        int score = capturePlayerScores.merge(damagerTeam, 1, Integer::sum);
        jailCapturePlayer(map, target, targetTeam);
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + damager.getName()
                + " captured " + target.getName() + " for Team " + damagerTeam + " (" + score + "/5).");
        if (score >= plugin.getConfig().getInt("capture-players.captures-to-win", 5)) {
            List<UUID> winners = session.teams().entrySet().stream()
                    .filter(entry -> damagerTeam.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .filter(session.participants()::contains)
                    .toList();
            eventManager.endActiveEvent(winners.isEmpty() ? List.of(damager.getUniqueId()) : winners);
        }
    }

    private void jailCapturePlayer(EventMap map, Player player, String team) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        CuboidRegion jail = teamArea(map, "jail", team);
        Location location = jail == null ? center(map.areas().get("jail-zone")) : center(jail);
        if (location != null) {
            TeleportService.teleport(plugin, player, location, "capture players jail");
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You were captured. Wait for a teammate to free you.");
    }

    private void respawnCapturePlayer(EventSession session, EventMap map, Player player, String message) {
        String team = session.teams().getOrDefault(player.getUniqueId(), "1");
        Location spawn = map.spawns().get("team-" + team + "-spawn");
        if (spawn == null && !map.spawns().isEmpty()) {
            spawn = map.spawns().values().iterator().next();
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        if (spawn != null) {
            TeleportService.teleport(plugin, player, spawn, "capture players respawn");
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY + message);
    }

    private Location safeLocation(Player player, EventMap map) {
        Location safe = raceSafeLocation.get(player.getUniqueId());
        if (safe != null) {
            return safe;
        }
        if (!map.spawns().isEmpty()) {
            safe = map.spawns().values().iterator().next().clone();
        } else {
            safe = player.getLocation().clone();
        }
        raceSafeLocation.put(player.getUniqueId(), safe);
        return safe;
    }

    private void finishRace(Player player) {
        if (finishOrder.contains(player.getUniqueId())) {
            return;
        }
        finishOrder.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You finished.");
        eventManager.finishParticipant(player);
    }

    private void tickHotPotato(EventSession session) {
        List<UUID> participants = eventManager.activeParticipants();
        if (participants.size() <= 1) {
            return;
        }
        if (hotPotatoHolder == null || !participants.contains(hotPotatoHolder)) {
            hotPotatoHolder = participants.get(ThreadLocalRandom.current().nextInt(participants.size()));
            hotPotatoSeconds = 15;
            Player holder = Bukkit.getPlayer(hotPotatoHolder);
            if (holder != null) {
                holder.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You have the hot potato. Hit another player to pass it.");
            }
            return;
        }
        hotPotatoSeconds--;
        Player holder = Bukkit.getPlayer(hotPotatoHolder);
        if (holder != null && (hotPotatoSeconds == 10 || hotPotatoSeconds == 5 || hotPotatoSeconds <= 3)) {
            holder.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Hot potato explodes in " + hotPotatoSeconds + "s.");
        }
        if (hotPotatoSeconds <= 0 && holder != null) {
            UUID eliminated = hotPotatoHolder;
            hotPotatoHolder = null;
            eventManager.eliminateParticipant(holder, "held the hot potato too long");
            if (session.participants().contains(eliminated)) {
                session.participants().remove(eliminated);
            }
        }
    }

    private void tickRedLightGreenLight(EventSession session) {
        redLightSeconds--;
        if (redLightSeconds <= 0) {
            greenLight = !greenLight;
            redLightSeconds = greenLight ? 5 : 4;
            redLightLastSafeLocation.clear();
            EventMap map = session.selectedMap();
            if (map != null) {
                fillArea(map.areas().get("light-display"), greenLight ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
            }
            String state = greenLight ? ChatColor.GREEN + "Green light." : ChatColor.RED + "Red light. Stop moving.";
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + state);
            for (UUID uuid : session.participants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    redLightLastSafeLocation.put(uuid, player.getLocation().clone());
                }
            }
            return;
        }
        if (greenLight) {
            return;
        }
        for (UUID uuid : List.copyOf(session.participants())) {
            Player player = Bukkit.getPlayer(uuid);
            Location previous = redLightLastSafeLocation.get(uuid);
            if (player == null || previous == null) {
                continue;
            }
            Location now = player.getLocation();
            if (previous.getWorld() != null && now.getWorld() != null && previous.getWorld().getUID().equals(now.getWorld().getUID())
                    && (Math.abs(previous.getX() - now.getX()) > 0.12D || Math.abs(previous.getY() - now.getY()) > 0.12D
                    || Math.abs(previous.getZ() - now.getZ()) > 0.12D)) {
                eventManager.eliminateParticipant(player, "moved on red light");
            }
        }
    }

    private void ensureBlockPartyTask() {
        if (blockPartyTask != null && !blockPartyTask.isCancelled()) {
            return;
        }
        blockPartyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EventSession current = eventManager.session();
            if (current == null || current.phase() != EventPhase.ACTIVE || current.definition().type() != EventType.BLOCK_PARTY) {
                if (blockPartyTask != null) {
                    blockPartyTask.cancel();
                    blockPartyTask = null;
                }
                return;
            }
            tickBlockParty(current);
        }, 0L, 5L);
    }

    private void tickBlockParty(EventSession session) {
        if (blockPartyClearDelay > 0) {
            blockPartyClearDelay--;
            if (blockPartyClearDelay == 0) {
                blockPartyTarget = null;
            }
            return;
        }

        if (blockPartyTarget == null) {
            startBlockPartyRound(session);
            return;
        }

        blockPartyTicksRemaining--;
        blockPartySeconds = Math.max(0, (int) Math.ceil(blockPartyTicksRemaining / 4.0D));
        updateBlockPartyTimer(session);
        playBlockPartyCountdownSound(session);
        if (blockPartyTicksRemaining <= 0) {
            EventMap map = session.selectedMap();
            if (map != null) {
                clearWrongBlockPartyColors(map.areas().get("color-floor"), blockPartyTarget);
            }
            playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 0.7F);
            blockPartyClearDelay = 8;
            blockPartyRound++;
            eventManager.setRuntimeScoreboardValue("block-party-round", String.valueOf(blockPartyRound));
        }
    }

    private void startBlockPartyRound(EventSession session) {
        blockPartyTarget = BLOCK_PARTY_COLORS.get(ThreadLocalRandom.current().nextInt(BLOCK_PARTY_COLORS.size()));
        eventManager.setRuntimeScoreboardValue("block-party-round", String.valueOf(blockPartyRound + 1));
        blockPartyRoundTicks = blockPartyRoundTicks();
        blockPartyTicksRemaining = blockPartyRoundTicks;
        blockPartySeconds = Math.max(1, (int) Math.ceil(blockPartyTicksRemaining / 4.0D));
        blockPartySoundStage = 0;
        EventMap map = session.selectedMap();
        if (map != null) {
            fillBlockPartyPattern(map.areas().get("color-floor"));
        }
        fillBlockPartyHotbars(session);
        updateBlockPartyTimer(session);
        playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 1.0F);
        messageParticipants(session, ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Stand on "
                + ChatColor.WHITE + readableMaterial(blockPartyTarget) + ChatColor.YELLOW + ".");
    }

    private int blockPartyRoundTicks() {
        if (blockPartyRound < 10) {
            return Math.max(12, (9 - (blockPartyRound / 2)) * 4);
        }
        double seconds = Math.max(1.0D, 3.0D - ((blockPartyRound - 9) * 0.10D));
        return Math.max(4, (int) Math.round(seconds * 4.0D));
    }

    private void playBlockPartyCountdownSound(EventSession session) {
        if (blockPartyRoundTicks <= 0 || blockPartySoundStage >= 3) {
            return;
        }
        int elapsed = blockPartyRoundTicks - blockPartyTicksRemaining;
        int first;
        int second;
        int third;
        if (blockPartyRoundTicks > 12) {
            first = Math.max(1, blockPartyRoundTicks - 12);
            second = Math.max(first + 1, blockPartyRoundTicks - 8);
            third = Math.max(second + 1, blockPartyRoundTicks - 4);
        } else {
            first = Math.max(1, (int) Math.round(blockPartyRoundTicks * 0.25D));
            second = Math.max(first + 1, (int) Math.round(blockPartyRoundTicks * 0.50D));
            third = Math.max(second + 1, (int) Math.round(blockPartyRoundTicks * 0.75D));
            third = Math.min(third, blockPartyRoundTicks - 1);
        }
        if ((blockPartySoundStage == 0 && elapsed >= first)
                || (blockPartySoundStage == 1 && elapsed >= second)
                || (blockPartySoundStage == 2 && elapsed >= third)) {
            playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
            blockPartySoundStage++;
        }
    }

    private void tickBedWarsGenerators(EventSession session) {
        bedWarsGeneratorTicks++;
        if (bedWarsGeneratorTicks % 2 != 0) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        map.generators().forEach((type, locations) -> {
            String lower = type.toLowerCase(Locale.ROOT);
            Material material;
            if (lower.equals("solo") || lower.equals("team") || lower.startsWith("solo-") || lower.startsWith("team-")) {
                material = bedWarsGeneratorTicks % 8 == 0 ? Material.GOLD_INGOT : Material.IRON_INGOT;
            } else if (lower.equals("diamond")) {
                material = Material.DIAMOND;
            } else if (lower.equals("emerald")) {
                material = Material.EMERALD;
            } else if (lower.equals("gold")) {
                material = Material.GOLD_INGOT;
            } else {
                material = Material.IRON_INGOT;
            }
            for (Location location : locations) {
                if (location != null && location.getWorld() != null) {
                    location.getWorld().dropItemNaturally(location.clone().add(0.5D, 0.5D, 0.5D), new org.bukkit.inventory.ItemStack(material));
                }
            }
        });
    }

    private boolean canBreakSpleefBlock(EventMap map, Block block) {
        CuboidRegion breakableArea = map.areas().get("breakable-area");
        if (breakableArea != null && !breakableArea.contains(block.getLocation())) {
            return false;
        }
        Location materialSource = map.points().get("breakable-block");
        return materialSource == null
                || materialSource.getWorld() == null
                || materialSource.getBlock().getType() == block.getType();
    }

    private void ensureSession(EventSession session) {
        if (trackedSession != session) {
            resetForNewSession();
            trackedSession = session;
        }
    }

    private boolean isRaceType(EventType type) {
        return type == EventType.PARKOUR
                || type == EventType.BOAT_RACE
                || type == EventType.HORSE_RACE
                || type == EventType.ELYTRA_RACE;
    }

    private boolean usesFallElimination(EventType type) {
        return type == EventType.SPLEEF
                || type == EventType.SPLEEG
                || type == EventType.SUMO_1V1
                || type == EventType.SUMO_2V2
                || type == EventType.SUMO_FFA
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.BLOCK_PARTY
                || type == EventType.QUAKE
                || type == EventType.SKYWARS
                || type == EventType.BEDWARS
                || type == EventType.FIGHT_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.FIGHT_FFA
                || type == EventType.ONE_IN_THE_CHAMBER
                || type == EventType.HOT_POTATO;
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void fireQuake(Player shooter, EventSession session) {
        if (!quakeCooldownReady(shooter, quakeShotCooldowns)) {
            return;
        }
        Location eye = shooter.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        for (double distance = 0.0D; distance <= 80.0D; distance += 1.2D) {
            Location point = eye.clone().add(direction.clone().multiply(distance));
            shooter.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9F, 1.7F);
        RayTraceResult result = shooter.getWorld().rayTraceEntities(
                eye,
                direction,
                80.0D,
                0.45D,
                entity -> entity instanceof Player target
                        && !target.getUniqueId().equals(shooter.getUniqueId())
                        && session.participants().contains(target.getUniqueId())
                        && !quakeRespawns.containsKey(target.getUniqueId())
        );
        if (result != null && result.getHitEntity() instanceof Player target) {
            addQuakeScore(shooter, 1);
            addQuakeScore(target, -1);
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + shooter.getName()
                    + " hit " + target.getName() + ".");
            scheduleQuakeRespawn(session, target);
        }
    }

    private void launchQuake(Player player) {
        if (!quakeCooldownReady(player, quakeLaunchCooldowns)) {
            return;
        }
        Vector direction = player.getLocation().getDirection().normalize();
        Vector horizontal = direction.clone().setY(0.0D);
        if (horizontal.lengthSquared() > 0.0D) {
            horizontal.normalize().multiply(1.55D);
        }
        double upward = Math.max(0.65D, 0.65D + Math.max(0.0D, direction.getY()) * 1.25D);
        Vector velocity = horizontal.setY(upward);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9F, 1.25F);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0D, 0.2D, 0.0D), 16, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    private void addQuakeScore(Player player, int delta) {
        int score = quakeScores.merge(player.getUniqueId(), delta, Integer::sum);
        eventManager.setRuntimeScoreboardValue("quake-score-" + player.getUniqueId(), String.valueOf(score));
    }

    private void scheduleQuakeRespawn(EventSession session, Player player) {
        if (quakeRespawns.containsKey(player.getUniqueId())) {
            return;
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "You will respawn in 3 seconds.");
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            quakeRespawns.remove(player.getUniqueId());
            EventSession current = eventManager.session();
            if (current == null || current != session || current.phase() != EventPhase.ACTIVE
                    || current.definition().type() != EventType.QUAKE
                    || !current.participants().contains(player.getUniqueId()) || !player.isOnline()) {
                return;
            }
            EventMap map = current.selectedMap();
            Location spawn = randomSpawn(map);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(10.0F);
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector(0, 0, 0));
            if (!player.getInventory().contains(Material.GOLDEN_HOE)) {
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.GOLDEN_HOE));
            }
            if (spawn != null) {
                TeleportService.teleport(plugin, player, spawn, "quake respawn");
            }
        }, 60L);
        quakeRespawns.put(player.getUniqueId(), task);
    }

    private Location randomSpawn(EventMap map) {
        if (map == null || map.spawns().isEmpty()) {
            return null;
        }
        List<Location> spawns = List.copyOf(map.spawns().values());
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())).clone();
    }

    private boolean quakeCooldownReady(Player player, Map<UUID, Long> cooldowns) {
        long now = System.currentTimeMillis();
        long previous = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 3000L) {
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private boolean inArea(EventMap map, String key, Location location) {
        CuboidRegion area = map.areas().get(key);
        return area != null && area.contains(location);
    }

    private CuboidRegion teamArea(EventMap map, String prefix, String team) {
        CuboidRegion area = map.areas().get(prefix + "-" + team);
        if (area != null) {
            return area;
        }
        return map.areas().get(prefix + "-zone");
    }

    private Location center(CuboidRegion area) {
        if (area == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                (area.minX() + area.maxX()) / 2.0D + 0.5D,
                area.minY() + 1.0D,
                (area.minZ() + area.maxZ()) / 2.0D + 0.5D
        );
    }

    private boolean isBedBlock(Material material) {
        return material.name().endsWith("_BED");
    }

    private void handleBedWarsBedBreak(EventSession session, EventMap map, BlockBreakEvent event) {
        String bedTeam = nearestBedTeam(map, event.getBlock().getLocation());
        if (bedTeam == null) {
            return;
        }
        String breakerTeam = session.teams().get(event.getPlayer().getUniqueId());
        if (bedTeam.equals(breakerTeam)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You cannot break your own bed.");
            return;
        }
        if (!brokenBedTeams.contains(bedTeam)) {
            brokenBedTeams.add(bedTeam);
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Team " + bedTeam + "'s bed was destroyed.");
        }
    }

    private Location bedWarsRespawn(EventSession session, EventMap map, Player player) {
        if (map == null) {
            return null;
        }
        String team = session.teams().get(player.getUniqueId());
        Location spawn = team == null ? null : map.spawns().get("team-" + team + "-spawn");
        if (spawn == null && !map.spawns().isEmpty()) {
            spawn = map.spawns().values().iterator().next();
        }
        return spawn;
    }

    private String nearestBedTeam(EventMap map, Location location) {
        String nearest = null;
        double distance = Double.MAX_VALUE;
        for (Map.Entry<String, Location> entry : map.points().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("bed-") || entry.getValue() == null || entry.getValue().getWorld() == null
                    || location == null || location.getWorld() == null
                    || !entry.getValue().getWorld().getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            double current = entry.getValue().distanceSquared(location);
            if (current <= 4.0D && current < distance) {
                distance = current;
                nearest = key.substring("bed-".length());
            }
        }
        return nearest;
    }

    private String reachedFlagTeam(EventMap map, Location location) {
        for (Map.Entry<String, Location> entry : map.points().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("flag-")) {
                continue;
            }
            Location point = entry.getValue();
            if (point != null && location != null && point.getWorld() != null && location.getWorld() != null
                    && point.getWorld().getUID().equals(location.getWorld().getUID())
                    && point.distanceSquared(location) <= 4.0D) {
                return key.substring("flag-".length());
            }
        }
        return null;
    }

    private String nearestTeamByFlag(EventMap map, Location location) {
        String nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Location> entry : map.points().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("flag-")) {
                continue;
            }
            Location point = entry.getValue();
            if (point == null || location == null || point.getWorld() == null || location.getWorld() == null
                    || !point.getWorld().getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            double distance = point.distanceSquared(location);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = key.substring("flag-".length());
            }
        }
        return nearest;
    }

    private void fillArea(CuboidRegion area, Material material) {
        if (area == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized event area color update (" + blocks + " blocks). Resize the special area.");
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private void fillBlockPartyPattern(CuboidRegion area) {
        if (area == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized block party pattern update (" + blocks + " blocks). Resize the special area.");
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean placedTarget = false;
        double targetChance = blockPartyTargetChance();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material material;
                    if (random.nextDouble() < targetChance) {
                        material = blockPartyTarget;
                    } else {
                        material = randomNonTargetBlockPartyColor(random);
                    }
                    if (material == blockPartyTarget) {
                        placedTarget = true;
                    }
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
        if (!placedTarget) {
            world.getBlockAt((minX + maxX) / 2, minY, (minZ + maxZ) / 2).setType(blockPartyTarget, false);
        }
    }

    private double blockPartyTargetChance() {
        double baseChance = 1.0D / BLOCK_PARTY_COLORS.size();
        if (blockPartyRound < 10) {
            return baseChance;
        }
        double reduction = Math.max(0.18D, 1.0D - ((blockPartyRound - 9) * 0.07D));
        return baseChance * reduction;
    }

    private Material randomNonTargetBlockPartyColor(ThreadLocalRandom random) {
        Material material;
        do {
            material = BLOCK_PARTY_COLORS.get(random.nextInt(BLOCK_PARTY_COLORS.size()));
        } while (material == blockPartyTarget);
        return material;
    }

    private void clearWrongBlockPartyColors(CuboidRegion area, Material target) {
        if (area == null || target == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized block party clear update (" + blocks + " blocks). Resize the special area.");
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (BLOCK_PARTY_COLORS.contains(block.getType()) && block.getType() != target) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void fillBlockPartyHotbars(EventSession session) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            for (int slot = 0; slot < 9; slot++) {
                player.getInventory().setItem(slot, new org.bukkit.inventory.ItemStack(blockPartyTarget));
            }
            player.updateInventory();
        }
    }

    private void updateBlockPartyTimer(EventSession session) {
        float progress = blockPartyRoundTicks <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, blockPartyTicksRemaining / (float) blockPartyRoundTicks));
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.setLevel(Math.max(0, blockPartySeconds));
            player.setExp(progress);
        }
    }

    private void messageParticipants(EventSession session, String message) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void playForParticipants(EventSession session, Sound sound, float volume, float pitch) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private List<Map.Entry<String, Location>> orderedCheckpoints(EventMap map) {
        return map.checkpoints().entrySet().stream()
                .sorted((left, right) -> checkpointOrder(left.getKey()) - checkpointOrder(right.getKey()))
                .toList();
    }

    private Map.Entry<String, CuboidRegion> reachedRaceArea(EventMap map, Location location, EventType type) {
        if (map == null || location == null) {
            return null;
        }
        return map.areas().entrySet().stream()
                .filter(entry -> isRaceAreaKey(entry.getKey()))
                .filter(entry -> entry.getValue() != null && containsRaceArea(entry.getValue(), location, type))
                .sorted((left, right) -> checkpointOrder(left.getKey()) - checkpointOrder(right.getKey()))
                .findFirst()
                .orElse(null);
    }

    private boolean containsRaceArea(CuboidRegion area, Location location, EventType type) {
        if (area.contains(location)) {
            return true;
        }
        if (type != EventType.HORSE_RACE) {
            return false;
        }
        if (location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(area.worldName())) {
            return false;
        }
        return location.getX() >= area.minX() && location.getX() <= area.maxX()
                && location.getZ() >= area.minZ() && location.getZ() <= area.maxZ()
                && location.getY() >= area.minY() && location.getY() <= area.maxY() + 4.0D;
    }

    private boolean isRaceAreaKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("checkpoint")
                || lower.startsWith("ring")
                || lower.startsWith("cp")
                || lower.startsWith("finish");
    }

    private Location checkpointRespawn(EventMap map, String key, Location fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        Location exact = map.points().get("checkpoint-spawn-" + normalized);
        if (exact != null) {
            return exact;
        }
        String number = checkpointNumber(normalized);
        if (!number.isBlank()) {
            Location numbered = map.points().get("checkpoint-spawn-" + number);
            if (numbered != null) {
                return numbered;
            }
            Location cpNumbered = map.points().get("checkpoint-spawn-cp" + number);
            if (cpNumbered != null) {
                return cpNumbered;
            }
            Location ringNumbered = map.points().get("checkpoint-spawn-ring" + number);
            if (ringNumbered != null) {
                return ringNumbered;
            }
        }
        return fallback;
    }

    private int checkpointOrder(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.equals("finish") || lower.startsWith("finish-")) {
            return Integer.MAX_VALUE;
        }
        String digits = lower.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return 10_000;
        }
        return Integer.parseInt(digits);
    }

    private Map.Entry<String, Location> nearestCheckpoint(EventMap map, Location location) {
        return orderedCheckpoints(map).stream()
                .filter(entry -> sameWorld(entry.getValue(), location) && entry.getValue().distanceSquared(location) <= 4.0D)
                .findFirst()
                .orElse(null);
    }

    private String checkpointMessage(String key) {
        String digits = checkpointNumber(key);
        if (digits.isBlank()) {
            return "Checkpoint reached.";
        }
        return "Reached Checkpoint #" + digits + ".";
    }

    private String checkpointNumber(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("\\D+", "");
    }

    private boolean isFinishCheckpoint(String key) {
        return key.toLowerCase(Locale.ROOT).startsWith("finish");
    }

    private String readableMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
