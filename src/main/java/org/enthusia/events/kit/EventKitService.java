package org.enthusia.events.kit;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventKitService {

    private final EnthusiaEventsPlugin plugin;
    private final File file;
    private final Map<String, EventKit> kits = new HashMap<>();
    private final Map<UUID, String> selectedKits = new HashMap<>();

    public EventKitService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        File directory = new File(plugin.getDataFolder(), "kits");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create kits folder.");
        }
        this.file = new File(directory, "kits.yml");
        reload();
    }

    public void reload() {
        kits.clear();
        if (!file.exists()) {
            save();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            kits.put(normalize(key), new EventKit(
                    key,
                    readItems(section, "contents", 36),
                    readItems(section, "armor", 4),
                    section.getItemStack("offhand")
            ));
        }
    }

    public boolean saveKit(Player player, String rawName) {
        String key = normalize(rawName);
        if (key.isBlank()) {
            return false;
        }
        kits.put(key, new EventKit(
                rawName,
                cloneArray(player.getInventory().getStorageContents(), 36),
                cloneArray(player.getInventory().getArmorContents(), 4),
                cloneItem(player.getInventory().getItemInOffHand())
        ));
        save();
        return true;
    }

    public boolean selectKit(Player player, String rawName) {
        String key = normalize(rawName);
        if (!kits.containsKey(key)) {
            return false;
        }
        selectedKits.put(player.getUniqueId(), key);
        return true;
    }

    public Optional<EventKit> selectedKit(Player player) {
        String selected = selectedKits.get(player.getUniqueId());
        if (selected != null && kits.containsKey(selected)) {
            return Optional.of(kits.get(selected));
        }
        return firstKit();
    }

    public void applySelectedKit(Player player) {
        selectedKit(player).ifPresent(kit -> {
            player.getInventory().setStorageContents(cloneArray(kit.contents(), 36));
            player.getInventory().setArmorContents(cloneArray(kit.armor(), 4));
            player.getInventory().setItemInOffHand(cloneItem(kit.offhand()));
            player.updateInventory();
        });
    }

    public Optional<EventKit> kit(String rawName) {
        return Optional.ofNullable(kits.get(normalize(rawName)));
    }

    public Optional<EventKit> firstKit() {
        return kits.values().stream()
                .sorted(Comparator.comparing(EventKit::name, String.CASE_INSENSITIVE_ORDER))
                .findFirst();
    }

    public List<EventKit> kits() {
        return kits.values().stream()
                .sorted(Comparator.comparing(EventKit::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void clearSelection(UUID uuid) {
        selectedKits.remove(uuid);
    }

    public void clearSelections() {
        selectedKits.clear();
    }

    public String selectedKitName(Player player) {
        return selectedKit(player).map(EventKit::name).orElse("default");
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 1);
        for (EventKit kit : kits.values()) {
            String path = "kits." + normalize(kit.name());
            yaml.set(path + ".display-name", kit.name());
            writeItems(yaml, path + ".contents", kit.contents());
            writeItems(yaml, path + ".armor", kit.armor());
            yaml.set(path + ".offhand", kit.offhand());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save kits/kits.yml: " + e.getMessage());
        }
    }

    private ItemStack[] readItems(ConfigurationSection section, String key, int size) {
        ItemStack[] items = new ItemStack[size];
        ConfigurationSection itemSection = section.getConfigurationSection(key);
        if (itemSection == null) {
            return items;
        }
        for (String slotKey : itemSection.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotKey);
                if (slot >= 0 && slot < size) {
                    items[slot] = itemSection.getItemStack(slotKey);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private void writeItems(YamlConfiguration yaml, String path, ItemStack[] items) {
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != null) {
                yaml.set(path + "." + slot, items[slot]);
            }
        }
    }

    private ItemStack[] cloneArray(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (source == null) {
            return copy;
        }
        for (int i = 0; i < Math.min(source.length, size); i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private String normalize(String value) {
        return ChatColor.stripColor(value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "");
    }

    public record EventKit(String name, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        public List<ItemStack> previewItems() {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : armor) {
                if (item != null) {
                    items.add(item.clone());
                }
            }
            for (ItemStack item : contents) {
                if (item != null) {
                    items.add(item.clone());
                }
            }
            if (offhand != null) {
                items.add(offhand.clone());
            }
            return items;
        }
    }
}
