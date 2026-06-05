package org.enthusia.events.event;

import org.bukkit.Material;

public record EventDefinition(
        EventType type,
        String displayName,
        Material icon,
        String description,
        boolean allowTeleport,
        boolean allowEnderPearl,
        boolean usesKits
) {
}
