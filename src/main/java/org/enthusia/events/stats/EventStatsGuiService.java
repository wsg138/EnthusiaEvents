package org.enthusia.events.stats;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventDefinition;
import org.enthusia.events.event.EventType;
import org.enthusia.events.skin.SkinCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EventStatsGuiService implements Listener {

    private static final int[] EVENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int[] LEADERBOARD_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final EnthusiaEventsPlugin plugin;
    private final EventStatsService statsService;
    private final SkinCache skinCache;

    public EventStatsGuiService(EnthusiaEventsPlugin plugin, EventStatsService statsService, SkinCache skinCache) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.skinCache = skinCache;
    }

    public void openMain(Player viewer) {
        Holder holder = new Holder(View.MAIN, null, LeaderboardSort.TOTAL_WINS, 1);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "Event Stats");
        holder.inventory(inventory);
        fill(inventory);
        PlayerEventStats stats = statsService.stats(viewer.getUniqueId());
        inventory.setItem(4, playerStatsItem(viewer.getUniqueId(), viewer.getName(), stats, null));
        List<EventDefinition> events = plugin.eventRegistry().all().stream()
                .sorted(Comparator.comparing(EventDefinition::displayName))
                .toList();
        for (int i = 0; i < events.size() && i < EVENT_SLOTS.length; i++) {
            EventDefinition definition = events.get(i);
            inventory.setItem(EVENT_SLOTS[i], eventItem(definition, stats));
        }
        inventory.setItem(48, button(Material.BOOK, ChatColor.AQUA + "Leaderboards",
                List.of(ChatColor.GRAY + "View top players by wins", ChatColor.GRAY + "or win ratio.")));
        inventory.setItem(50, button(Material.BARRIER, ChatColor.RED + "Close", List.of()));
        viewer.openInventory(inventory);
    }

    private void openEvent(Player viewer, EventType type) {
        Holder holder = new Holder(View.EVENT, type, LeaderboardSort.TOTAL_WINS, 1);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_AQUA + "Event Stats - " + displayName(type));
        holder.inventory(inventory);
        fill(inventory);
        PlayerEventStats stats = statsService.stats(viewer.getUniqueId());
        inventory.setItem(11, eventDetailItem(type, stats));
        inventory.setItem(15, leaderboardButton(type));
        inventory.setItem(18, button(Material.ARROW, ChatColor.YELLOW + "Back", List.of()));
        inventory.setItem(22, button(Material.BARRIER, ChatColor.RED + "Close", List.of()));
        viewer.openInventory(inventory);
    }

    private void openLeaderboard(Player viewer, LeaderboardSort sort, EventType type, int page) {
        int safePage = Math.max(1, page);
        EventType resolvedType = sort.usesEvent() ? eventTypeOrFirst(type) : type;
        Holder holder = new Holder(View.LEADERBOARD, resolvedType, sort, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "Event Leaderboard");
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(1, sortButton(LeaderboardSort.TOTAL_WINS, sort));
        inventory.setItem(3, sortButton(LeaderboardSort.TOTAL_RATIO, sort));
        inventory.setItem(5, sortButton(LeaderboardSort.EVENT_WINS, sort));
        inventory.setItem(7, sortButton(LeaderboardSort.EVENT_RATIO, sort));
        inventory.setItem(13, eventFilterButton(resolvedType));

        List<LeaderboardRow> rows = leaderboard(sort, resolvedType);
        int pageSize = LEADERBOARD_SLOTS.length;
        int from = (safePage - 1) * pageSize;
        for (int i = 0; i < pageSize && from + i < rows.size(); i++) {
            inventory.setItem(LEADERBOARD_SLOTS[i], leaderboardItem(from + i + 1, rows.get(from + i), sort, resolvedType));
        }
        inventory.setItem(45, button(Material.ARROW, ChatColor.YELLOW + "Previous Page", List.of(ChatColor.GRAY + "Page " + Math.max(1, safePage - 1))));
        inventory.setItem(48, button(Material.OAK_DOOR, ChatColor.YELLOW + "Back", List.of()));
        inventory.setItem(49, button(Material.BARRIER, ChatColor.RED + "Close", List.of()));
        inventory.setItem(53, button(Material.ARROW, ChatColor.YELLOW + "Next Page", List.of(ChatColor.GRAY + "Page " + (safePage + 1))));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (holder.view() == View.MAIN) {
            handleMainClick(player, slot);
        } else if (holder.view() == View.EVENT) {
            handleEventClick(player, holder, slot);
        } else {
            handleLeaderboardClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
                event.setCancelled(true);
            }
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == 48) {
            openLeaderboard(player, LeaderboardSort.TOTAL_WINS, null, 1);
            return;
        }
        if (slot == 50) {
            player.closeInventory();
            return;
        }
        List<EventDefinition> events = plugin.eventRegistry().all().stream()
                .sorted(Comparator.comparing(EventDefinition::displayName))
                .toList();
        for (int i = 0; i < EVENT_SLOTS.length && i < events.size(); i++) {
            if (EVENT_SLOTS[i] == slot) {
                openEvent(player, events.get(i).type());
                return;
            }
        }
    }

    private void handleEventClick(Player player, Holder holder, int slot) {
        if (slot == 15) {
            openLeaderboard(player, LeaderboardSort.EVENT_WINS, holder.eventType(), 1);
        } else if (slot == 18) {
            openMain(player);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }

    private void handleLeaderboardClick(Player player, Holder holder, int slot) {
        if (slot == 1) {
            openLeaderboard(player, LeaderboardSort.TOTAL_WINS, null, 1);
        } else if (slot == 3) {
            openLeaderboard(player, LeaderboardSort.TOTAL_RATIO, null, 1);
        } else if (slot == 5) {
            openLeaderboard(player, LeaderboardSort.EVENT_WINS, eventTypeOrFirst(holder.eventType()), 1);
        } else if (slot == 7) {
            openLeaderboard(player, LeaderboardSort.EVENT_RATIO, eventTypeOrFirst(holder.eventType()), 1);
        } else if (slot == 13) {
            openLeaderboard(player, holder.sort(), nextEventType(eventTypeOrFirst(holder.eventType())), 1);
        } else if (slot == 45) {
            openLeaderboard(player, holder.sort(), holder.eventType(), Math.max(1, holder.page() - 1));
        } else if (slot == 48) {
            openMain(player);
        } else if (slot == 49) {
            player.closeInventory();
        } else if (slot == 53) {
            openLeaderboard(player, holder.sort(), holder.eventType(), holder.page() + 1);
        }
    }

    private List<LeaderboardRow> leaderboard(LeaderboardSort sort, EventType type) {
        Comparator<Map.Entry<UUID, PlayerEventStats>> comparator = switch (sort) {
            case TOTAL_RATIO -> Comparator
                    .comparingDouble((Map.Entry<UUID, PlayerEventStats> entry) -> entry.getValue().winRatio()).reversed()
                    .thenComparing(entry -> entry.getValue().wins(), Comparator.reverseOrder());
            case EVENT_WINS -> Comparator
                    .comparingInt((Map.Entry<UUID, PlayerEventStats> entry) -> entry.getValue().winsByEvent().getOrDefault(type, 0)).reversed()
                    .thenComparing(entry -> entry.getValue().playedByEvent().getOrDefault(type, 0), Comparator.reverseOrder());
            case EVENT_RATIO -> Comparator
                    .comparingDouble((Map.Entry<UUID, PlayerEventStats> entry) -> entry.getValue().winRatio(type)).reversed()
                    .thenComparing(entry -> entry.getValue().winsByEvent().getOrDefault(type, 0), Comparator.reverseOrder());
            default -> Comparator
                    .comparingInt((Map.Entry<UUID, PlayerEventStats> entry) -> entry.getValue().wins()).reversed()
                    .thenComparing(entry -> entry.getValue().eventsPlayed(), Comparator.reverseOrder());
        };
        return statsService.allStats().entrySet().stream()
                .filter(entry -> hasLeaderboardValue(entry.getValue(), sort, type))
                .sorted(comparator)
                .map(entry -> new LeaderboardRow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean hasLeaderboardValue(PlayerEventStats stats, LeaderboardSort sort, EventType type) {
        return switch (sort) {
            case TOTAL_RATIO -> stats.eventsPlayed() > 0;
            case EVENT_WINS, EVENT_RATIO -> type != null && stats.playedByEvent().getOrDefault(type, 0) > 0;
            default -> stats.eventsPlayed() > 0;
        };
    }

    private ItemStack playerStatsItem(UUID uuid, String fallbackName, PlayerEventStats stats, EventType type) {
        ItemStack item = skinCache.createHead(uuid, ChatColor.GOLD + fallbackName);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(statsLore(stats, type));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack eventItem(EventDefinition definition, PlayerEventStats stats) {
        ItemStack item = new ItemStack(definition.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + definition.displayName());
        meta.setLore(statsLore(stats, definition.type()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack eventDetailItem(EventType type, PlayerEventStats stats) {
        return button(Material.BOOK, ChatColor.GOLD + displayName(type), statsLore(stats, type));
    }

    private ItemStack leaderboardButton(EventType type) {
        return button(Material.NETHER_STAR, ChatColor.AQUA + "View " + displayName(type) + " Leaderboard",
                List.of(ChatColor.GRAY + "Click to rank by wins for this event."));
    }

    private ItemStack sortButton(LeaderboardSort sort, LeaderboardSort selected) {
        Material material = switch (sort) {
            case TOTAL_RATIO, EVENT_RATIO -> Material.LIME_DYE;
            case EVENT_WINS -> Material.DIAMOND;
            default -> Material.GOLD_INGOT;
        };
        String name = (sort == selected ? ChatColor.GREEN.toString() + ChatColor.BOLD : ChatColor.YELLOW.toString()) + sort.label();
        return button(material, name,
                List.of(sort == selected ? ChatColor.GREEN + "Selected" : ChatColor.GRAY + "Click to sort."));
    }

    private ItemStack eventFilterButton(EventType type) {
        return button(Material.COMPASS, ChatColor.AQUA + "Event: " + displayName(type),
                List.of(ChatColor.GRAY + "Click to cycle event leaderboards."));
    }

    private ItemStack leaderboardItem(int rank, LeaderboardRow row, LeaderboardSort sort, EventType type) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(row.uuid());
        String name = offline.getName() == null ? row.uuid().toString().substring(0, 8) : offline.getName();
        ItemStack item = skinCache.createHead(row.uuid(), ChatColor.AQUA + "#" + rank + " " + name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(statsLore(row.stats(), sort.usesEvent() ? type : null));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> statsLore(PlayerEventStats stats, EventType type) {
        int played = type == null ? stats.eventsPlayed() : stats.playedByEvent().getOrDefault(type, 0);
        int wins = type == null ? stats.wins() : stats.winsByEvent().getOrDefault(type, 0);
        int losses = type == null ? stats.losses() : stats.lossesByEvent().getOrDefault(type, 0);
        double ratio = played <= 0 ? 0.0D : wins * 100.0D / played;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Games: " + ChatColor.WHITE + played);
        lore.add(ChatColor.GRAY + "Wins: " + ChatColor.GREEN + wins);
        lore.add(ChatColor.GRAY + "Losses: " + ChatColor.RED + losses);
        lore.add(ChatColor.GRAY + "Win Ratio: " + ChatColor.AQUA + String.format(Locale.ROOT, "%.1f%%", ratio));
        if (type == null) {
            lore.add(ChatColor.GRAY + "Current Streak: " + ChatColor.YELLOW + stats.winStreak());
            lore.add(ChatColor.GRAY + "Best Streak: " + ChatColor.GOLD + stats.bestStreak());
        }
        return lore;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private EventType nextEventType(EventType current) {
        List<EventDefinition> events = plugin.eventRegistry().all().stream()
                .sorted(Comparator.comparing(EventDefinition::displayName))
                .toList();
        if (events.isEmpty()) {
            return null;
        }
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).type() == current) {
                return events.get((i + 1) % events.size()).type();
            }
        }
        return events.get(0).type();
    }

    private EventType eventTypeOrFirst(EventType type) {
        if (type != null) {
            return type;
        }
        return plugin.eventRegistry().all().stream()
                .sorted(Comparator.comparing(EventDefinition::displayName))
                .map(EventDefinition::type)
                .findFirst()
                .orElse(null);
    }

    private String displayName(EventType type) {
        if (type == null) {
            return "All Events";
        }
        EventDefinition definition = plugin.eventRegistry().definition(type);
        return definition == null ? type.name().replace('_', ' ') : definition.displayName();
    }

    private enum View {
        MAIN,
        EVENT,
        LEADERBOARD
    }

    private enum LeaderboardSort {
        TOTAL_WINS("All Wins", false),
        TOTAL_RATIO("All Win Ratio", false),
        EVENT_WINS("Event Wins", true),
        EVENT_RATIO("Event Win Ratio", true);

        private final String label;
        private final boolean usesEvent;

        LeaderboardSort(String label, boolean usesEvent) {
            this.label = label;
            this.usesEvent = usesEvent;
        }

        private String label() {
            return label;
        }

        private boolean usesEvent() {
            return usesEvent;
        }
    }

    private record LeaderboardRow(UUID uuid, PlayerEventStats stats) {
    }

    private static final class Holder implements InventoryHolder {
        private final View view;
        private final EventType eventType;
        private final LeaderboardSort sort;
        private final int page;
        private Inventory inventory;

        private Holder(View view, EventType eventType, LeaderboardSort sort, int page) {
            this.view = view;
            this.eventType = eventType;
            this.sort = sort;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private View view() {
            return view;
        }

        private EventType eventType() {
            return eventType;
        }

        private LeaderboardSort sort() {
            return sort;
        }

        private int page() {
            return page;
        }
    }
}
