package org.enthusia.events.setup;

import org.bukkit.inventory.ItemStack;

public record SetupInventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
}
