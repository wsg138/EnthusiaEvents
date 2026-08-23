package org.enthusia.events.event;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Small gameplay polish kept separate from the large gameplay listener.
 */
public final class BedWarsPolishListener implements Listener {

    private static final String ITEM_SHOP_TITLE = "BedWars Item Shop";
    private static final String BOAT_RACE_TAG = "enthusia_boat_race_boat";
    private static final double FIREBALL_DAMAGE_CAP = 1.0D;
    private static final float FIREBALL_EXPLOSION_POWER = 2.0F;
    private static final double FIREBALL_SPEED = 1.5D;
    private static final int MULTIVERSE_SCAN_INTERVAL_TICKS = 100;
    private static final List<BlockFace> WATER_CHECK_FACES = List.of(
            BlockFace.UP,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    );

    private final EventManager eventManager;
    private final Set<UUID> boatFinishFeedbackSent = new LinkedHashSet<>();
    private final Set<UUID> multiverseRegistrationAttempts = new LinkedHashSet<>();
    private EventSession trackedBoatSession;
    private int multiverseScanTicks;

    public BedWarsPolishListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    /**
     * BedWars is team-last-standing, not player-last-standing. The generic manager only
     * closes last-standing events when one individual participant remains, which leaves a
     * winning BedWars team with multiple survivors stuck in ACTIVE forever. This periodic
     * check closes the match as soon as only one represented team remains.
     */
    public void tickWinCondition() {
        tickMultiverseRegistration();

        EventSession session = activeBedWarsSession();
        if (session == null || session.participants().isEmpty()) {
            return;
        }

        Set<String> remainingTeams = new LinkedHashSet<>();
        for (UUID uuid : session.participants()) {
            String team = normalizedTeam(eventManager.teamFor(uuid));
            if (team.isBlank()) {
                return;
            }
            remainingTeams.add(team);
            if (remainingTeams.size() > 1) {
                return;
            }
        }

        if (remainingTeams.size() != 1) {
            return;
        }
        String winningTeam = remainingTeams.iterator().next();
        List<UUID> winners = session.participants().stream()
                .filter(uuid -> winningTeam.equals(normalizedTeam(eventManager.teamFor(uuid))))
                .toList();
        if (!winners.isEmpty()) {
            eventManager.endActiveEventDelayed(winners, 60L);
        }
    }

    /**
     * Boat Race's main finish handler removes the finisher from participants before it
     * broadcasts the finish announcement, so the player who actually finished is excluded
     * from that broadcast. Capture the crossing here and confirm it after the normal finish
     * handler has recorded the ranking.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBoatRaceMove(VehicleMoveEvent event) {
        EventSession session = eventManager.session();
        if (session == null
                || session.phase() != EventPhase.ACTIVE
                || session.definition() == null
                || session.definition().type() != EventType.BOAT_RACE) {
            if (trackedBoatSession != session) {
                trackedBoatSession = session;
                boatFinishFeedbackSent.clear();
            }
            return;
        }
        if (trackedBoatSession != session) {
            trackedBoatSession = session;
            boatFinishFeedbackSent.clear();
        }
        if (!event.getVehicle().getScoreboardTags().contains(BOAT_RACE_TAG)) {
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
        boolean crossingFinish = map.checkpoints().entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).startsWith("finish"))
                .map(java.util.Map.Entry::getValue)
                .anyMatch(finish -> sameBlock(finish, event.getTo()));
        if (!crossingFinish) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("EnthusiaEvents");
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            EventSession current = eventManager.session();
            if (current != session || !current.finalRankings().contains(playerId)
                    || !boatFinishFeedbackSent.add(playerId)) {
                return;
            }
            Player finisher = Bukkit.getPlayer(playerId);
            if (finisher == null || !finisher.isOnline()) {
                return;
            }
            int place = current.finalRankings().indexOf(playerId) + 1;
            finisher.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                    + "You finished! " + ChatColor.YELLOW + "Place: #" + place);
        });
    }

    /**
     * Show the shop material in the buyer's team color. The reward id stored on the item
     * remains unchanged, so the normal BedWars purchase handler still owns the transaction.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedWarsShopOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !ITEM_SHOP_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }

        String team = eventManager.teamFor(player.getUniqueId());
        for (ItemStack stack : event.getView().getTopInventory().getContents()) {
            recolorStack(stack, team);
        }
    }

    /**
     * The main shop listener completes the purchase at HIGHEST priority. MONITOR runs after
     * that, so the purchased ItemStack is recolored while it is an inventory item rather
     * than later when the block is placed.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedWarsInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        recolorBuildingItems(player);
    }

    /**
     * Recolor mined/dropped concrete or terracotta before it enters the receiving player's
     * inventory. After this, placing, dropping, storing, and re-mining it naturally keeps
     * that team's material color until another team picks it up.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedWarsItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }

        ItemStack stack = event.getItem().getItemStack();
        if (recolorStack(stack, eventManager.teamFor(player.getUniqueId()))) {
            event.getItem().setItemStack(stack);
        }
    }

    /**
     * The existing gameplay listener spawns the fireball synchronously at HIGHEST priority.
     * By MONITOR priority it already exists, so tune that newly-spawned projectile without
     * duplicating the purchase/use logic.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedWarsFireballUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.FIRE_CHARGE) {
            return;
        }

        Player player = event.getPlayer();
        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }

        Fireball fireball = newestOwnedFireball(player);
        if (fireball == null) {
            return;
        }

        Vector direction = player.getEyeLocation().getDirection().normalize();
        fireball.setDirection(direction);
        fireball.setVelocity(direction.clone().multiply(FIREBALL_SPEED));
        fireball.setYield(FIREBALL_EXPLOSION_POWER);
        fireball.setIsIncendiary(false);
    }

    /**
     * Hypixel-style fireballs remove soft bed-defense blocks, not End Stone, glass, or
     * obsidian. The main gameplay listener separately limits explosions to event-placed
     * blocks, so the two filters combine safely.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedWarsFireballExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball) || !isBedWarsFireball(fireball)) {
            return;
        }
        event.blockList().removeIf(block -> !isFireballBreakable(block));
    }

    /**
     * BedWars fireballs are a knockback/boost utility first. Keep direct damage very low
     * and apply a strong outward impulse, with a slightly more vertical self-boost so
     * fireball jumping behaves much closer to Hypixel BedWars.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBedWarsFireballDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fireball fireball)
                || !(event.getEntity() instanceof Player target)
                || !isBedWarsFireball(fireball)) {
            return;
        }

        EventSession session = activeBedWarsSession();
        if (session == null || !session.participants().contains(target.getUniqueId())) {
            return;
        }

        event.setDamage(Math.min(event.getDamage(), FIREBALL_DAMAGE_CAP));

        Location explosion = fireball.getLocation().clone();
        boolean selfBoost = fireball.getShooter() instanceof Player shooter
                && shooter.getUniqueId().equals(target.getUniqueId());
        scheduleFireballKnockback(target, explosion, selfBoost);
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

    private void tickMultiverseRegistration() {
        multiverseScanTicks++;
        if (multiverseScanTicks < MULTIVERSE_SCAN_INTERVAL_TICKS) {
            return;
        }
        multiverseScanTicks = 0;
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            if (!isEventWorld(world) || !multiverseRegistrationAttempts.add(world.getUID())) {
                continue;
            }
            String worldArgument = usesNamespacedWorldKeys() ? world.getKey().toString() : world.getName();
            String environment = switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "the_end";
                default -> "normal";
            };
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + worldArgument + " " + environment);
        }
    }

    private boolean isEventWorld(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("events-") || name.startsWith("ee_");
    }

    private boolean usesNamespacedWorldKeys() {
        String version = Bukkit.getMinecraftVersion();
        int dot = version.indexOf('.');
        String majorText = dot >= 0 ? version.substring(0, dot) : version;
        try {
            return Integer.parseInt(majorText) >= 26;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean sameBlock(Location left, Location right) {
        return left != null && right != null && left.getWorld() != null && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private Fireball newestOwnedFireball(Player player) {
        Fireball best = null;
        double bestDistance = Double.MAX_VALUE;
        Location eye = player.getEyeLocation();
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(eye, 4.0D, 4.0D, 4.0D)) {
            if (!(entity instanceof Fireball fireball)
                    || fireball.getTicksLived() > 2
                    || !(fireball.getShooter() instanceof Player shooter)
                    || !shooter.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            double distance = fireball.getLocation().distanceSquared(eye);
            if (distance < bestDistance) {
                best = fireball;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isBedWarsFireball(Fireball fireball) {
        EventSession session = activeBedWarsSession();
        if (session == null || !(fireball.getShooter() instanceof Player shooter)) {
            return false;
        }
        return session.participants().contains(shooter.getUniqueId());
    }

    private boolean isFireballBreakable(Block block) {
        if (block == null || isWaterProtected(block)) {
            return false;
        }
        String name = block.getType().name();
        return name.endsWith("_WOOL")
                || name.endsWith("_TERRACOTTA")
                || name.endsWith("_PLANKS")
                || block.getType() == Material.LADDER;
    }

    private boolean isWaterProtected(Block block) {
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return true;
        }
        for (BlockFace face : WATER_CHECK_FACES) {
            if (block.getRelative(face).getType() == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    private void scheduleFireballKnockback(Player target, Location explosion, boolean selfBoost) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("EnthusiaEvents");
        if (plugin == null || !plugin.isEnabled()) {
            applyFireballKnockback(target, explosion, selfBoost);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyFireballKnockback(target, explosion, selfBoost));
    }

    private void applyFireballKnockback(Player target, Location explosion, boolean selfBoost) {
        if (!target.isOnline() || target.getWorld() != explosion.getWorld()) {
            return;
        }

        Location targetLocation = target.getLocation();
        Vector away = targetLocation.toVector().subtract(explosion.toVector());
        double distance = away.length();
        Vector horizontal = new Vector(away.getX(), 0.0D, away.getZ());
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = new Vector(target.getVelocity().getX(), 0.0D, target.getVelocity().getZ());
        }
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = targetLocation.getDirection().setY(0.0D);
        }
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = new Vector(0.0D, 0.0D, 1.0D);
        }
        horizontal.normalize();

        double distanceFactor = Math.max(0.38D, 1.0D - Math.min(distance, 5.0D) / 6.5D);
        double horizontalStrength = (selfBoost ? 1.15D : 1.55D) * distanceFactor;
        double verticalStrength = (selfBoost ? 1.05D : 0.78D) * distanceFactor + 0.18D;

        Vector impulse = horizontal.multiply(horizontalStrength).setY(verticalStrength);
        Vector current = target.getVelocity();
        Vector result = new Vector(
                current.getX() * 0.30D,
                Math.max(0.0D, current.getY()) * 0.20D,
                current.getZ() * 0.30D
        ).add(impulse);
        target.setVelocity(result);
    }

    private void recolorBuildingItems(Player player) {
        String team = eventManager.teamFor(player.getUniqueId());
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (recolorStack(stack, team)) {
                player.getInventory().setItem(slot, stack);
            }
        }
    }

    private boolean recolorStack(ItemStack stack, String team) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Material teamMaterial = teamBuildingMaterial(stack.getType(), team);
        if (teamMaterial == null || teamMaterial == stack.getType()) {
            return false;
        }
        stack.setType(teamMaterial);
        return true;
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

    private Material teamBuildingMaterial(Material material, String team) {
        if (material == Material.TERRACOTTA || material.name().endsWith("_TERRACOTTA")) {
            return teamTerracotta(team);
        }
        if (material.name().endsWith("_CONCRETE")) {
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
