package org.enthusia.events.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Centralizes how EnthusiaEvents stores and resolves event worlds.
 *
 * <p>Event maps may live under the server's {@code Events/} directory while older
 * installations can continue to reference root-level names such as
 * {@code Events-BedWars}. When only the nested equivalent exists, the legacy name
 * is transparently resolved to {@code Events/-BedWars}.</p>
 */
public final class EventWorldPaths {

    public static final String EVENT_DIRECTORY = "Events";
    private static final String EVENT_DIRECTORY_PREFIX = EVENT_DIRECTORY + "/";
    private static final String LEGACY_EVENT_PREFIX = "Events-";
    private static final String MIGRATION_BACKUP_NAME = "maps.yml.pre-nested-world-migration.bak";

    private EventWorldPaths() {
    }

    public static String eventWorldName(String displayName) {
        String segment = sanitizeSegment(displayName);
        if (segment.isBlank()) {
            segment = "Map";
        }
        return EVENT_DIRECTORY_PREFIX + "-" + segment;
    }

    public static String sanitizeWorldName(String rawWorldName) {
        String raw = Optional.ofNullable(rawWorldName).orElse("").trim().replace('\\', '/');
        if (startsWithIgnoreCase(raw, EVENT_DIRECTORY_PREFIX)) {
            String child = raw.substring(EVENT_DIRECTORY_PREFIX.length()).replace('/', '-');
            boolean keepLeadingDash = child.startsWith("-");
            String sanitizedChild = sanitizeSegment(child);
            if (sanitizedChild.isBlank()) {
                sanitizedChild = "Map";
            }
            return EVENT_DIRECTORY_PREFIX + (keepLeadingDash ? "-" : "") + sanitizedChild;
        }
        String sanitized = sanitizeSegment(raw);
        return sanitized.isBlank() ? LEGACY_EVENT_PREFIX + "Map" : sanitized;
    }

    public static String resolveExistingWorldName(String configuredWorldName) {
        if (configuredWorldName == null || configuredWorldName.isBlank()) {
            return configuredWorldName;
        }
        String configured = configuredWorldName.trim().replace('\\', '/');
        World loaded = Bukkit.getWorld(configured);
        if (loaded != null) {
            return loaded.getName();
        }
        File direct = new File(Bukkit.getWorldContainer(), configured);
        if (direct.isDirectory()) {
            return configured;
        }
        String nested = nestedAlias(configured);
        if (nested == null) {
            return configured;
        }
        World nestedLoaded = Bukkit.getWorld(nested);
        if (nestedLoaded != null) {
            return nestedLoaded.getName();
        }
        File nestedFolder = new File(Bukkit.getWorldContainer(), nested);
        if (nestedFolder.isDirectory()) {
            return nested;
        }
        String caseMatched = caseInsensitiveNestedAlias(configured);
        return caseMatched == null ? configured : caseMatched;
    }

    public static World loadWorld(String configuredWorldName) {
        String resolved = resolveExistingWorldName(configuredWorldName);
        if (resolved == null || resolved.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(resolved);
        if (world != null) {
            return world;
        }
        return Bukkit.createWorld(new WorldCreator(resolved));
    }

    public static boolean isEventWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        String lower = worldName.replace('\\', '/').toLowerCase(Locale.ROOT);
        return lower.startsWith("events/") || lower.startsWith("events-") || lower.startsWith("ee_");
    }

    /**
     * Rewrites legacy root-level event-world references in maps.yml only when the
     * matching nested world actually exists and the root-level world does not.
     * A one-time backup is created beside maps.yml before any rewrite.
     */
    public static boolean migrateMapsFile(File mapsFile, Logger logger) {
        if (mapsFile == null || !mapsFile.isFile()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mapsFile);
        boolean changed = false;
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof String value) || value.isBlank()) {
                continue;
            }
            String migrated = migrateStoredString(value);
            if (!value.equals(migrated)) {
                yaml.set(entry.getKey(), migrated);
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }
        File backup = new File(mapsFile.getParentFile(), MIGRATION_BACKUP_NAME);
        try {
            if (!backup.exists()) {
                Files.copy(mapsFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            yaml.save(mapsFile);
            if (logger != null) {
                logger.info("Migrated event map world references into Events/. Backup: " + backup.getName());
            }
            return true;
        } catch (IOException ex) {
            if (logger != null) {
                logger.warning("Could not migrate nested event-world references: " + ex.getMessage());
            }
            return false;
        }
    }

    private static String migrateStoredString(String value) {
        int comma = value.indexOf(',');
        String worldPart = comma >= 0 ? value.substring(0, comma) : value;
        String resolved = resolveExistingWorldName(worldPart);
        if (resolved == null || resolved.equals(worldPart)) {
            return value;
        }
        return comma >= 0 ? resolved + value.substring(comma) : resolved;
    }

    private static String nestedAlias(String worldName) {
        if (!startsWithIgnoreCase(worldName, LEGACY_EVENT_PREFIX)) {
            return null;
        }
        String suffix = worldName.substring(LEGACY_EVENT_PREFIX.length());
        String segment = sanitizeSegment(suffix);
        if (segment.isBlank()) {
            return null;
        }
        return EVENT_DIRECTORY_PREFIX + "-" + segment;
    }

    private static String caseInsensitiveNestedAlias(String worldName) {
        if (!startsWithIgnoreCase(worldName, LEGACY_EVENT_PREFIX)) {
            return null;
        }
        File eventsDirectory = new File(Bukkit.getWorldContainer(), EVENT_DIRECTORY);
        if (!eventsDirectory.isDirectory()) {
            return null;
        }
        String wanted = "-" + sanitizeSegment(worldName.substring(LEGACY_EVENT_PREFIX.length()));
        File[] children = eventsDirectory.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.getName().equalsIgnoreCase(wanted)) {
                return EVENT_DIRECTORY_PREFIX + child.getName();
            }
        }
        return null;
    }

    private static String sanitizeSegment(String value) {
        return Optional.ofNullable(value).orElse("")
                .replaceAll("[^A-Za-z0-9_\\-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
