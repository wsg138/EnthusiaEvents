package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-event safety rules that should remain true regardless of individual game logic.
 * This owns hub/trophy safety and the BedWars base/chest rules found during production retesting.
 */
public final class EventSessionHardeningListener implements Listener {

    private static final List<Long> DEATHMATCH_COUNTDOWN_SECONDS = List.of(300L, 120L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private EventSession countdownSession;
    private final Set<Long> deathmatchAnnouncements = new HashSet<>();

    public EventSessionHardeningListener(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    /**
     * Called immediately before EventManager sends participants to their map spawns.
     * Their real pre-event state is already snapshotted, so temporary cleanup here is safe.
     */
    public void prepareSession(EventSession session) {
        if (session == null) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            player.setFireTicks(0);
            player.setFreezeTicks(0);
        }
        if (session.definition().type() == EventType.BEDWARS) {
            clearBedWarsChests(session.selectedMap());
        }
    }

    /** Runs once per second from the plugin scheduler. */
    public void tick() {
        purgeProtectedWorlds();
        tickBedWarsDeathmatchCountdown();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isHubOrTrophyProtected(player.getLocation())) {
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedDeath(PlayerDeathEvent event) {
        if (!isHubOrTrophyProtected(event.getEntity().getLocation())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedCreatureSpawn(CreatureSpawnEvent event) {
        if (isHubOrTrophyProtected(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedItemSpawn(ItemSpawnEvent event) {
        if (isHubOrTrophyProtected(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedWarsBlockPlace(BlockPlaceEvent event) {
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (isBedWarsSpawnProtected(plugin, session.selectedMap(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(ChatColor.RED + "You cannot place blocks in a team spawn zone.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedWarsBucketEmpty(PlayerBucketEmptyEvent event) {
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Location target = event.getBlock().getRelative(event.getBlockFace()).getLocation();
        if (isBedWarsSpawnProtected(plugin, session.selectedMap(), target)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(ChatColor.RED + "You cannot place liquids in a team spawn zone.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedWarsFluidFlow(BlockFromToEvent event) {
        EventSession session = activeBedWarsSession();
        if (session == null) {
            return;
        }
        Material source = event.getBlock().getType();
        if (source != Material.WATER && source != Material.LAVA) {
            return;
        }
        if (isBedWarsSpawnProtected(plugin, session.selectedMap(), event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Right-clicking a chest at your own BedWars base deposits the held stack instead of
     * opening the chest. Sneaking deposits exactly one item. Empty-hand clicks still open it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedWarsTeamChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !isChest(event.getClickedBlock().getType())) {
            return;
        }
        EventSession session = activeBedWarsSession();
        Player player = event.getPlayer();
        if (session == null || !session.participants().contains(player.getUniqueId())
                || !isOwnTeamChest(session, player, event.getClickedBlock().getLocation())) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Chest chest)) {
            return;
        }

        event.setCancelled(true);
        int requested = player.isSneaking() ? 1 : held.getAmount();
        ItemStack deposit = held.clone();
        deposit.setAmount(requested);
        Map<Integer, ItemStack> leftovers = chest.getInventory().addItem(deposit);
        int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = requested - left;
        if (moved <= 0) {
            player.sendActionBar(ChatColor.RED + "That team chest is full.");
            return;
        }
        int remaining = held.getAmount() - moved;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(remaining);
            player.getInventory().setItemInMainHand(held);
        }
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.45F, 1.35F);
    }

    public static boolean isBedWarsSpawnProtected(EnthusiaEventsPlugin plugin, EventMap map, Location location) {
        if (plugin == null || map == null || location == null || location.getWorld() == null) {
            return false;
        }
        for (Map.Entry<String, Location> entry : map.spawns().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("team-") || !key.endsWith("-spawn")) {
                continue;
            }
            String team = key.substring("team-".length(), key.length() - "-spawn".length());
            for (String areaKey : List.of(
                    "spawn-protection-" + team,
                    "team-" + team + "-spawn-protection",
                    "spawn-zone-" + team,
                    "team-" + team + "-spawn-zone")) {
                CuboidRegion area = map.areas().get(areaKey);
                if (area != null && area.contains(location)) {
                    return true;
                }
            }

            Location spawn = entry.getValue();
            if (!sameWorld(spawn, location)) {
                continue;
            }
            double radius = Math.max(0.0D, plugin.getConfig().getDouble("bedwars.spawn-protection-radius", 3.0D));
            double below = Math.max(0.0D, plugin.getConfig().getDouble("bedwars.spawn-protection-below", 2.0D));
            double above = Math.max(0.0D, plugin.getConfig().getDouble("bedwars.spawn-protection-above", 4.0D));
            double dx = spawn.getX() - location.getX();
            double dz = spawn.getZ() - location.getZ();
            double dy = location.getY() - spawn.getY();
            if ((dx * dx) + (dz * dz) <= radius * radius && dy >= -below && dy <= above) {
                return true;
            }
        }
        return false;
    }

    private EventSession activeBedWarsSession() {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE
                || session.definition().type() != EventType.BEDWARS) {
            return null;
        }
        return session;
    }

    private void clearBedWarsChests(EventMap map) {
        if (map == null || map.region() == null) {
            return;
        }
        World world = Bukkit.getWorld(map.region().worldName());
        if (world == null) {
            return;
        }
        int minChunkX = ((int) Math.floor(map.region().minX())) >> 4;
        int maxChunkX = ((int) Math.floor(map.region().maxX())) >> 4;
        int minChunkZ = ((int) Math.floor(map.region().minZ())) >> 4;
        int maxChunkZ = ((int) Math.floor(map.region().maxZ())) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Chest chest && map.region().contains(state.getLocation())) {
                        chest.getInventory().clear();
                    }
                }
            }
        }
    }

    private boolean isOwnTeamChest(EventSession session, Player player, Location chestLocation) {
        EventMap map = session.selectedMap();
        if (map == null || chestLocation == null) {
            return false;
        }
        String team = eventManager.teamFor(player.getUniqueId());
        Location ownSpawn = map.spawns().get("team-" + team + "-spawn");
        if (!sameWorld(ownSpawn, chestLocation)) {
            return false;
        }
        double maxRadius = Math.max(1.0D, plugin.getConfig().getDouble("bedwars.team-chest-radius", 10.0D));
        if (horizontalDistanceSquared(ownSpawn, chestLocation) > maxRadius * maxRadius
                || Math.abs(ownSpawn.getY() - chestLocation.getY()) > 6.0D) {
            return false;
        }
        double ownDistance = ownSpawn.distanceSquared(chestLocation);
        for (Map.Entry<String, Location> entry : map.spawns().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("team-") || !key.endsWith("-spawn") || entry.getValue() == ownSpawn
                    || !sameWorld(entry.getValue(), chestLocation)) {
                continue;
            }
            if (entry.getValue().distanceSquared(chestLocation) < ownDistance) {
                return false;
            }
        }
        return true;
    }

    private void purgeProtectedWorlds() {
        Set<World> protectedWorlds = new LinkedHashSet<>();
        addWorld(protectedWorlds, eventManager.waitingHubLocation());
        addWorld(protectedWorlds, eventManager.trophyRoomLocation());
        CuboidRegion hub = eventManager.waitingHubRegion();
        CuboidRegion trophy = eventManager.trophyRoomRegion();
        if (hub != null) {
            World world = Bukkit.getWorld(hub.worldName());
            if (world != null) {
                protectedWorlds.add(world);
            }
        }
        if (trophy != null) {
            World world = Bukkit.getWorld(trophy.worldName());
            if (world != null) {
                protectedWorlds.add(world);
            }
        }
        for (World world : protectedWorlds) {
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (isHubOrTrophyProtected(mob.getLocation())) {
                    mob.remove();
                }
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isHubOrTrophyProtected(item.getLocation())) {
                    item.remove();
                }
            }
        }
    }

    private boolean isHubOrTrophyProtected(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        CuboidRegion hub = eventManager.waitingHubRegion();
        CuboidRegion trophy = eventManager.trophyRoomRegion();
        if ((hub != null && hub.contains(location)) || (trophy != null && trophy.contains(location))) {
            return true;
        }

        Location hubLocation = eventManager.waitingHubLocation();
        Location trophyLocation = eventManager.trophyRoomLocation();
        boolean dedicatedProtectedWorld = sameWorld(hubLocation, location) || sameWorld(trophyLocation, location);
        if (!dedicatedProtectedWorld) {
            return false;
        }

        EventSession session = eventManager.session();
        EventMap activeMap = eventManager.activeMap();
        if (session != null && activeMap != null && activeMap.region() != null
                && (session.phase() == EventPhase.PRESTART || session.phase() == EventPhase.ACTIVE)
                && activeMap.region().contains(location)) {
            return false;
        }
        return true;
    }

    private void tickBedWarsDeathmatchCountdown() {
        EventSession session = eventManager.session();
        if (session != countdownSession) {
            countdownSession = session;
            deathmatchAnnouncements.clear();
        }
        if (session == null || session.phase() != EventPhase.ACTIVE
                || session.definition().type() != EventType.BEDWARS) {
            return;
        }
        long activeRemaining = eventManager.activeSecondsRemaining();
        if (activeRemaining <= 300L) {
            return;
        }
        long untilDeathmatch = activeRemaining - 300L;
        for (long threshold : DEATHMATCH_COUNTDOWN_SECONDS) {
            if (untilDeathmatch <= threshold && untilDeathmatch >= threshold - 1L
                    && deathmatchAnnouncements.add(threshold)) {
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW
                        + "Deathmatch in " + formatDuration(threshold) + ". Remaining beds will be removed.");
                if (threshold <= 10L) {
                    for (UUID uuid : session.participants()) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 1.1F);
                        }
                    }
                }
            }
        }
    }

    private String formatDuration(long seconds) {
        if (seconds >= 60L && seconds % 60L == 0L) {
            long minutes = seconds / 60L;
            return minutes + " minute" + (minutes == 1L ? "" : "s");
        }
        return seconds + " second" + (seconds == 1L ? "" : "s");
    }

    private static boolean isChest(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }

    private static boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID());
    }

    private static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return (dx * dx) + (dz * dz);
    }

    private static void addWorld(Set<World> worlds, Location location) {
        if (location != null && location.getWorld() != null) {
            worlds.add(location.getWorld());
        }
    }
}
