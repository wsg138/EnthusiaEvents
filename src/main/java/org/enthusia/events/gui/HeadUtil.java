package org.enthusia.events.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.util.UUID;

public final class HeadUtil {

    private HeadUtil() {
    }

    public static ItemStack createPlayerHead(EnthusiaEventsPlugin plugin, UUID uuid, String displayName) {
        ItemStack head = plugin.skinCache().createHead(uuid, displayName);
        if (head.getType() != Material.PLAYER_HEAD) {
            head = new ItemStack(Material.PLAYER_HEAD);
        }
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "owner-uuid"),
                    PersistentDataType.STRING,
                    uuid.toString()
            );
            head.setItemMeta(meta);
        }
        return head;
    }
}
