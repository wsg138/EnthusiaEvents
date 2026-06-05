package org.enthusia.events.skin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinCache {

    private final Map<UUID, ItemStack> cache = new ConcurrentHashMap<>();

    public void cacheFromOnline(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setOwningPlayer(player);
        head.setItemMeta(meta);
        cache.put(player.getUniqueId(), head);
    }

    public ItemStack createHead(UUID uuid, String displayName) {
        ItemStack head = cache.get(uuid);
        if (head != null && head.getType() == Material.PLAYER_HEAD) {
            head = head.clone();
        } else {
            head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                meta.setOwningPlayer(offline);
                head.setItemMeta(meta);
            }
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null && displayName != null) {
            meta.setDisplayName(displayName);
            head.setItemMeta(meta);
        }
        return head;
    }
}
