package org.enthusia.events.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RestoreConfirmGui {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public RestoreConfirmGui(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.actionKey = new NamespacedKey(plugin, "restore-action");
        this.targetKey = new NamespacedKey(plugin, "restore-target");
    }

    public void open(Player admin, Player target) {
        Inventory inventory = Bukkit.createInventory(new RestoreHolder(), 27, "Confirm Restore");
        inventory.setItem(11, item(Material.LIME_CONCRETE, "confirm", target, ChatColor.GREEN + "Confirm Restore", List.of(
                ChatColor.GRAY + "Restores " + target.getName() + "'s saved event snapshot.",
                ChatColor.RED + "This overwrites their current inventory and location."
        )));
        inventory.setItem(15, item(Material.RED_CONCRETE, "cancel", target, ChatColor.RED + "Cancel", List.of(
                ChatColor.GRAY + "Do not restore anything."
        )));
        admin.openInventory(inventory);
        plugin.messages().send(admin, "restore-confirm-opened", Map.of("player", target.getName()));
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RestoreHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR || !current.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = current.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        String targetRaw = pdc.get(targetKey, PersistentDataType.STRING);
        if (action == null || targetRaw == null) {
            return;
        }
        admin.closeInventory();
        if (action.equals("cancel")) {
            plugin.messages().send(admin, "restore-cancelled");
            return;
        }
        Player target = Bukkit.getPlayer(UUID.fromString(targetRaw));
        if (target == null) {
            plugin.messages().send(admin, "event-no-snapshot");
            return;
        }
        plugin.messages().send(admin, "event-restore-started", Map.of("player", target.getName()));
        eventManager.restoreSnapshot(target).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () ->
                plugin.messages().send(admin, restored ? "event-restored" : "event-restore-failed-staff",
                        Map.of("player", target.getName()))));
    }

    private ItemStack item(Material material, String action, Player target, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static final class RestoreHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
