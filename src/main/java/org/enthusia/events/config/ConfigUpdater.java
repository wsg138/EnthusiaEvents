package org.enthusia.events.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static void mergeDefaults(JavaPlugin plugin, String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            return;
        }

        InputStream stream = plugin.getResource(resourceName);
        if (stream == null) {
            return;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        boolean changed = mergeSection(current, defaults);
        if (!changed) {
            return;
        }

        try {
            current.save(file);
            plugin.getLogger().info("Updated missing defaults in " + resourceName + ".");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update " + resourceName + ": " + e.getMessage());
        }
    }

    private static boolean mergeSection(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);
            if (defaultValue instanceof ConfigurationSection defaultSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                    changed = true;
                }
                changed |= mergeSection(targetSection, defaultSection);
            } else if (!target.contains(key)) {
                target.set(key, defaultValue);
                changed = true;
            }
        }
        return changed;
    }
}
