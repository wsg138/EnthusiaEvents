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
import org.enthusia.events.event.EventDefinition;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;

import java.util.List;
import java.util.Map;

@SuppressWarnings("PMD.NPathComplexity")
public final class EventVoteGui {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final NamespacedKey eventTypeKey;

    public EventVoteGui(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.eventTypeKey = new NamespacedKey(plugin, "event-type");
    }

    public void openStart(Player player) {
        if (eventManager.hasSession()) {
            plugin.messages().send(player, "event-start-failed", Map.of(
                    "reason", eventManager.manualStartFailureReason(player, null, false)
            ));
            return;
        }
        List<EventDefinition> choices = eventManager.availableStartChoices();
        if (choices.isEmpty()) {
            plugin.messages().send(player, "vote-empty");
            return;
        }
        int size = Math.min(54, Math.max(27, (choices.size() + 8) / 9 * 9));
        Inventory inventory = Bukkit.createInventory(new StartHolder(), size, "Start Event");
        int slot = 0;
        for (EventDefinition definition : choices) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot++, eventItem(definition, "Click to start this event."));
        }
        player.openInventory(inventory);
    }

    public void open(Player player) {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.VOTE) {
            plugin.messages().send(player, "vote-not-active");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new VoteHolder(), 27, "Event Vote");
        int slot = 11;
        for (Map.Entry<EventType, Integer> entry : session.votes().entrySet()) {
            EventDefinition definition = plugin.eventRegistry().definition(entry.getKey());
            if (definition != null) {
                inventory.setItem(slot++, voteItem(definition, entry.getValue()));
            }
        }
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof VoteHolder) && !(holder instanceof StartHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR || !current.hasItemMeta()) {
            return;
        }
        PersistentDataContainer pdc = current.getItemMeta().getPersistentDataContainer();
        String typeName = pdc.get(eventTypeKey, PersistentDataType.STRING);
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        EventType eventType = EventType.valueOf(typeName);
        if (holder instanceof StartHolder) {
            player.closeInventory();
            EventDefinition definition = plugin.eventRegistry().definition(eventType);
            if (definition == null || !eventManager.startManualVote(player, definition, false)) {
                plugin.messages().send(player, "event-start-failed", Map.of(
                        "reason", eventManager.manualStartFailureReason(player, definition, false)
                ));
            }
            return;
        }
        eventManager.castVote(player, eventType);
        open(player);
    }

    public void closeVoteViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof VoteHolder) {
                player.closeInventory();
            }
        }
    }

    private ItemStack voteItem(EventDefinition definition, int votes) {
        ItemStack item = new ItemStack(definition.icon(), Math.max(1, Math.min(64, votes)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + definition.displayName());
        meta.setLore(List.of(
                ChatColor.GRAY + "Votes: " + ChatColor.WHITE + votes,
                ChatColor.GRAY + definition.description()
        ));
        meta.getPersistentDataContainer().set(eventTypeKey, PersistentDataType.STRING, definition.type().name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack eventItem(EventDefinition definition, String action) {
        ItemStack item = new ItemStack(definition.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + definition.displayName());
        meta.setLore(List.of(
                ChatColor.GRAY + definition.description(),
                ChatColor.GREEN + action
        ));
        meta.getPersistentDataContainer().set(eventTypeKey, PersistentDataType.STRING, definition.type().name());
        item.setItemMeta(meta);
        return item;
    }

    private static final class VoteHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class StartHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
