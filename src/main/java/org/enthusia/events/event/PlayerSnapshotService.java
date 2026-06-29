package org.enthusia.events.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.LocationCodec;
import org.enthusia.events.util.TeleportService;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerSnapshotService {

    private static final long SNAPSHOT_TTL_MILLIS = Duration.ofDays(7).toMillis();
    private static final int[] RESTORE_RETRY_DELAYS_TICKS = {20, 40, 80, 120};

    private final EnthusiaEventsPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> capturedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> restored = new ConcurrentHashMap<>();

    public PlayerSnapshotService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "snapshots.yml");
        load();
    }

    public void capture(Player player) {
        purgeExpired();
        snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        capturedAt.put(player.getUniqueId(), System.currentTimeMillis());
        restored.put(player.getUniqueId(), false);
        save();
    }

    public boolean hasSnapshot(UUID uuid) {
        purgeExpired();
        return snapshots.containsKey(uuid);
    }

    public boolean hasUnrestoredSnapshot(UUID uuid) {
        purgeExpired();
        return snapshots.containsKey(uuid) && !restored.getOrDefault(uuid, false);
    }

    public boolean restore(Player player, boolean consume) {
        purgeExpired();
        PlayerSnapshot snapshot = snapshots.get(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }

        if (snapshot.location() != null) {
            restoreWithRetry(player, snapshot, consume, 0);
        } else {
            applySnapshot(player, snapshot);
            markRestored(player.getUniqueId(), consume);
        }
        return true;
    }

    private void restoreWithRetry(Player player, PlayerSnapshot snapshot, boolean consume, int attempt) {
        if (!player.isOnline()) {
            return;
        }
        TeleportService.teleport(plugin, player, snapshot.location(), "restore event snapshot")
                .thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        applySnapshot(player, snapshot);
                        markRestored(player.getUniqueId(), consume);
                        return;
                    }
                    if (attempt < RESTORE_RETRY_DELAYS_TICKS.length) {
                        int delay = RESTORE_RETRY_DELAYS_TICKS[attempt];
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                                () -> restoreWithRetry(player, snapshot, consume, attempt + 1), delay);
                        return;
                    }
                    plugin.getLogger().warning("Could not restore event snapshot for " + player.getName()
                            + ". Snapshot was kept for a later retry.");
                    plugin.messages().send(player, "event-restore-failed");
                }));
    }

    public int retryPendingOnlineRestores() {
        int attempted = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hasUnrestoredSnapshot(player.getUniqueId())) {
                restore(player, false);
                attempted++;
            }
        }
        return attempted;
    }

    private void markRestored(UUID uuid, boolean consume) {
        if (consume) {
            snapshots.remove(uuid);
            capturedAt.remove(uuid);
            restored.remove(uuid);
        } else {
            restored.put(uuid, true);
        }
        save();
    }

    private void applySnapshot(Player player, PlayerSnapshot snapshot) {
        if (!player.isOnline()) {
            return;
        }
        player.closeInventory();
        player.getInventory().setContents(snapshot.inventory());
        player.getInventory().setArmorContents(snapshot.armor());
        player.getInventory().setItemInOffHand(snapshot.offhand());
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        player.setTotalExperience(snapshot.totalExperience());
        player.setGameMode(snapshot.gameMode());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying());
        player.setWalkSpeed(snapshot.walkSpeed());
        player.setFlySpeed(snapshot.flySpeed());
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        snapshot.potionEffects().forEach(player::addPotionEffect);
        player.setHealth(Math.min(snapshot.health(), player.getMaxHealth()));
        player.updateInventory();
    }

    public void discard(UUID uuid) {
        snapshots.remove(uuid);
        capturedAt.remove(uuid);
        restored.remove(uuid);
        save();
    }

    public void save() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder for event snapshots.");
                return;
            }
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerSnapshot> entry : snapshots.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerSnapshot snapshot = entry.getValue();
                String path = "players." + uuid;
                config.set(path + ".captured-at", capturedAt.getOrDefault(uuid, System.currentTimeMillis()));
                config.set(path + ".restored", restored.getOrDefault(uuid, false));
                config.set(path + ".location", LocationCodec.encode(snapshot.location()));
                config.set(path + ".inventory", nullableList(snapshot.inventory()));
                config.set(path + ".armor", nullableList(snapshot.armor()));
                config.set(path + ".offhand", snapshot.offhand());
                config.set(path + ".health", snapshot.health());
                config.set(path + ".food-level", snapshot.foodLevel());
                config.set(path + ".saturation", snapshot.saturation());
                config.set(path + ".exhaustion", snapshot.exhaustion());
                config.set(path + ".total-experience", snapshot.totalExperience());
                config.set(path + ".game-mode", snapshot.gameMode().name());
                config.set(path + ".potion-effects", new ArrayList<>(snapshot.potionEffects()));
                config.set(path + ".allow-flight", snapshot.allowFlight());
                config.set(path + ".flying", snapshot.flying());
                config.set(path + ".walk-speed", snapshot.walkSpeed());
                config.set(path + ".fly-speed", snapshot.flySpeed());
            }

            File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            config.save(tempFile);
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save event player snapshots.", ex);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("players")) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String key : Objects.requireNonNull(config.getConfigurationSection("players")).getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                long timestamp = config.getLong(path + ".captured-at", 0L);
                if (timestamp <= 0L || now - timestamp > SNAPSHOT_TTL_MILLIS) {
                    changed = true;
                    continue;
                }
                Location location = LocationCodec.decode(config.getString(path + ".location", ""));
                if (location == null) {
                    changed = true;
                    continue;
                }
                PlayerSnapshot snapshot = new PlayerSnapshot(
                        location,
                        itemStackArray(config.getList(path + ".inventory"), 41),
                        itemStackArray(config.getList(path + ".armor"), 4),
                        config.getItemStack(path + ".offhand"),
                        config.getDouble(path + ".health", 20.0D),
                        config.getInt(path + ".food-level", 20),
                        (float) config.getDouble(path + ".saturation", 5.0D),
                        (float) config.getDouble(path + ".exhaustion", 0.0D),
                        config.getInt(path + ".total-experience", 0),
                        parseGameMode(config.getString(path + ".game-mode", GameMode.SURVIVAL.name())),
                        potionEffects(config.getList(path + ".potion-effects")),
                        config.getBoolean(path + ".allow-flight", false),
                        config.getBoolean(path + ".flying", false),
                        (float) config.getDouble(path + ".walk-speed", 0.2D),
                        (float) config.getDouble(path + ".fly-speed", 0.1D)
                );
                snapshots.put(uuid, snapshot);
                capturedAt.put(uuid, timestamp);
                restored.put(uuid, config.getBoolean(path + ".restored", false));
            } catch (RuntimeException ex) {
                changed = true;
                plugin.getLogger().log(Level.WARNING, "Skipped corrupt event snapshot entry: " + key, ex);
            }
        }
        if (changed) {
            save();
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Map.Entry<UUID, Long> entry : new LinkedHashMap<>(capturedAt).entrySet()) {
            if (now - entry.getValue() > SNAPSHOT_TTL_MILLIS) {
                snapshots.remove(entry.getKey());
                capturedAt.remove(entry.getKey());
                restored.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private List<ItemStack> nullableList(ItemStack[] items) {
        List<ItemStack> result = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            result.add(item);
        }
        return result;
    }

    private ItemStack[] itemStackArray(List<?> values, int fallbackSize) {
        if (values == null) {
            return new ItemStack[fallbackSize];
        }
        ItemStack[] items = new ItemStack[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof ItemStack item) {
                items[i] = item;
            }
        }
        return items;
    }

    private Collection<PotionEffect> potionEffects(List<?> values) {
        List<PotionEffect> effects = new ArrayList<>();
        if (values == null) {
            return effects;
        }
        for (Object value : values) {
            if (value instanceof PotionEffect effect) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return GameMode.SURVIVAL;
        }
    }
}
