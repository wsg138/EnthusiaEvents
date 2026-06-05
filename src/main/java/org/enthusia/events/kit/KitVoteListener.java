package org.enthusia.events.kit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;

import java.util.List;
import java.util.Locale;

public final class KitVoteListener implements Listener {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final EventKitService kitService;
    private final NamespacedKey kitKey;

    public KitVoteListener(EnthusiaEventsPlugin plugin, EventManager eventManager, EventKitService kitService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.kitService = kitService;
        this.kitKey = new NamespacedKey(plugin, "kit-vote");
    }

    public void giveVotingItems(Player player) {
        EventSession session = eventManager.session();
        if (session == null || !isKitVotingPhase(session) || !isFightEvent(session.definition().type())) {
            return;
        }
        List<EventKitService.EventKit> kits = kitService.kits();
        if (kits.isEmpty()) {
            return;
        }
        int slot = 0;
        for (EventKitService.EventKit kit : kits) {
            if (slot > 8) {
                break;
            }
            player.getInventory().setItem(slot++, kitItem(kit));
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null || !isKitVotingPhase(session) || !eventManager.isEventPlayer(event.getPlayer().getUniqueId())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String kitName = item.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (kitName == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            openPreview(event.getPlayer(), kitName);
            return;
        }
        if (kitService.selectKit(event.getPlayer(), kitName)) {
            plugin.messages().send(event.getPlayer(), "kit-selected", java.util.Map.of("kit", kitName));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof KitPreviewHolder) {
            event.setCancelled(true);
        }
    }

    private void openPreview(Player player, String kitName) {
        EventKitService.EventKit kit = kitService.kit(kitName).orElse(null);
        if (kit == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(new KitPreviewHolder(), 54, "Kit Preview: " + kit.name());
        int slot = 0;
        for (ItemStack item : kit.previewItems()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot++, item);
        }
        player.openInventory(inventory);
    }

    private ItemStack kitItem(EventKitService.EventKit kit) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + kit.name());
            meta.setLore(List.of(
                    ChatColor.GRAY + "Right-click to select this kit.",
                    ChatColor.GRAY + "Sneak-right-click to preview."
            ));
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.name().toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isKitVotingPhase(EventSession session) {
        return session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN || session.phase() == EventPhase.PRESTART;
    }

    public static boolean isFightEvent(EventType type) {
        return type == EventType.FIGHT_1V1 || type == EventType.FIGHT_2V2 || type == EventType.FIGHT_FFA;
    }

    private static final class KitPreviewHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
