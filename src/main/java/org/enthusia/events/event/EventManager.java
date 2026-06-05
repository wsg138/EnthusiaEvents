package org.enthusia.events.event;

import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.config.EventConfigService;
import org.enthusia.events.kit.EventKitService;
import org.enthusia.events.loot.LootTableService;
import org.enthusia.events.reset.ArenaResetService;
import org.enthusia.events.scoreboard.EventScoreboardService;
import org.enthusia.events.stats.EventStatsService;
import org.enthusia.events.trophy.PodiumService;
import org.enthusia.events.util.LocationCodec;
import org.enthusia.events.util.TeleportService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class EventManager {

    public static final String EVENT_SCOREBOARD_TAG = "enthusia_event";
    public static final String EVENT_METADATA_KEY = "enthusia_event";

    private final EnthusiaEventsPlugin plugin;
    private final EventRegistry registry;
    private final PlayerSnapshotService snapshotService;
    private final EventStatsService statsService;
    private final Economy economy;
    private final MapSetupService mapSetupService;
    private final EventConfigService eventConfigService;
    private final EventKitService kitService;
    private final LootTableService lootTableService;
    private final Queue<EventDefinition> queue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, EventType> playerVotes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> allowedTeleports = new ConcurrentHashMap<>();
    private final Map<String, String> runtimeScoreboardValues = new ConcurrentHashMap<>();
    private final Set<UUID> spawnLocked = ConcurrentHashMap.newKeySet();
    private EventSession session;
    private BukkitTask phaseTask;
    private BukkitTask activeTask;
    private BukkitTask preStartTask;
    private int countdownRemaining;
    private int preStartRemaining;
    private int voteRemaining;
    private long activeEndsAtMillis;
    private boolean readyCountdownApplied;
    private Runnable voteCloseHandler = () -> {
    };
    private Consumer<Player> kitVotingItemsHandler = player -> {
    };
    private EventScoreboardService scoreboardService;
    private ArenaResetService arenaResetService;
    private PodiumService podiumService;
    private BoatRaceService boatRaceService;

    public EventManager(EnthusiaEventsPlugin plugin, EventRegistry registry, PlayerSnapshotService snapshotService,
                        EventStatsService statsService, Economy economy, MapSetupService mapSetupService,
                        EventConfigService eventConfigService, EventKitService kitService, LootTableService lootTableService) {
        this.plugin = plugin;
        this.registry = registry;
        this.snapshotService = snapshotService;
        this.statsService = statsService;
        this.economy = economy;
        this.mapSetupService = mapSetupService;
        this.eventConfigService = eventConfigService;
        this.kitService = kitService;
        this.lootTableService = lootTableService;
    }

    public List<EventDefinition> availableStartChoices() {
        return registry.all().stream()
                .filter(definition -> !mapSetupService.usableMapsFor(definition.type()).isEmpty())
                .toList();
    }

    public boolean hasSession() {
        return session != null;
    }

    public boolean startScheduledVote() {
        if (session != null) {
            randomAvailableChoices().stream().findFirst().ifPresent(definition -> {
                queue.add(definition);
                Bukkit.broadcastMessage(plugin.messages().format("event-queued", Map.of("event", definition.displayName())));
            });
            return false;
        }
        List<EventDefinition> choices = randomAvailableChoices();
        if (choices.isEmpty()) {
            Bukkit.broadcastMessage(plugin.messages().get("vote-empty"));
            return false;
        }
        createVoteSession(choices, null, false);
        broadcastVotePrompt();
        startVoteCountdown();
        return true;
    }

    public boolean startManualVote(CommandSender initiator, EventDefinition forced, boolean discountedRandom) {
        if (session != null) {
            return false;
        }
        List<EventDefinition> choices = forced == null ? randomAvailableChoices() : validChoice(forced);
        if (choices.isEmpty()) {
            return false;
        }
        if (initiator instanceof Player player && economy != null && !player.hasPermission("enthusia.events.start.free")) {
            double cost = discountedRandom
                    ? plugin.getConfig().getDouble("economy.random-start-cost", 750.0D)
                    : plugin.getConfig().getDouble("economy.manual-start-cost", 150.0D);
            if (!economy.has(player, cost)) {
                return false;
            }
            economy.withdrawPlayer(player, cost);
        }

        EventDefinition definition = choices.getFirst();
        if (initiator instanceof Player player) {
            runtimeScoreboardValues.clear();
            session = new EventSession(definition, EventPhase.JOIN);
            session.startedBy(player.getName());
            session.waitingHub(configuredLocation("locations.waiting-hub"));
            session.trophyRoom(configuredLocation("locations.trophy-room"));
            selectMapForSession();
            Bukkit.broadcastMessage(plugin.messages().format("manual-event-started-by", Map.of(
                    "event", definition.displayName(),
                    "player", player.getName()
            )));
            broadcastJoinPrompt(definition.displayName(), "player");
            startJoinCountdown();
        } else {
            createVoteSession(choices, null, false);
            broadcastVotePrompt();
            startVoteCountdown();
        }
        return true;
    }

    public String manualStartFailureReason(Player player, EventDefinition forced, boolean discountedRandom) {
        if (session != null) {
            return "a " + session.definition().displayName() + " event is already running";
        }
        if (forced != null && mapSetupService.usableMapsFor(forced.type()).isEmpty()) {
            return forced.displayName() + " has no completed map setup";
        }
        if (forced == null && availableStartChoices().isEmpty()) {
            return "no events with completed map setup are available";
        }
        if (player != null && economy != null && !player.hasPermission("enthusia.events.start.free")) {
            double cost = discountedRandom
                    ? plugin.getConfig().getDouble("economy.random-start-cost", 750.0D)
                    : plugin.getConfig().getDouble("economy.manual-start-cost", 150.0D);
            if (!economy.has(player, cost)) {
                return "you need " + cost + " to start an event";
            }
        }
        return "start conditions were not met";
    }

    public void voteCloseHandler(Runnable voteCloseHandler) {
        this.voteCloseHandler = voteCloseHandler == null ? () -> {
        } : voteCloseHandler;
    }

    public void kitVotingItemsHandler(Consumer<Player> kitVotingItemsHandler) {
        this.kitVotingItemsHandler = kitVotingItemsHandler == null ? player -> {
        } : kitVotingItemsHandler;
    }

    public void services(EventScoreboardService scoreboardService, ArenaResetService arenaResetService, PodiumService podiumService,
                         BoatRaceService boatRaceService) {
        this.scoreboardService = scoreboardService;
        this.arenaResetService = arenaResetService;
        this.podiumService = podiumService;
        this.boatRaceService = boatRaceService;
    }

    public boolean startForcedEvent(EventType type) {
        if (session != null) {
            return false;
        }
        EventDefinition definition = registry.definition(type);
        if (definition == null || mapSetupService.usableMapsFor(type).isEmpty()) {
            return false;
        }
        session = new EventSession(definition, EventPhase.JOIN);
        runtimeScoreboardValues.clear();
        session.adminStarted(true);
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        selectMapForSession();
        Bukkit.broadcastMessage(plugin.messages().format("force-started", Map.of("event", definition.displayName())));
        broadcastJoinPrompt(definition.displayName(), "staff");
        startJoinCountdown();
        return true;
    }

    public String forcedStartFailureReason(EventType type) {
        if (session != null) {
            return "a " + session.definition().displayName() + " event is already running";
        }
        if (registry.definition(type) == null) {
            return type.name() + " is not enabled in config.yml";
        }
        if (mapSetupService.usableMapsFor(type).isEmpty()) {
            return type.name() + " has no completed map setup";
        }
        return "start conditions were not met";
    }

    public boolean startQuickTest(Player player, EventType type) {
        if (session != null) {
            return false;
        }
        EventDefinition definition = registry.definition(type);
        if (definition == null) {
            return false;
        }
        List<EventMap> maps = mapSetupService.usableMapsFor(type);
        if (maps.isEmpty()) {
            return false;
        }
        EventMap map = maps.get(ThreadLocalRandom.current().nextInt(maps.size()));
        runtimeScoreboardValues.clear();
        session = new EventSession(definition, EventPhase.ACTIVE);
        session.adminStarted(true);
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        session.selectedMap(map);
        snapshotService.capture(player);
        session.participants().add(player.getUniqueId());
        markEventPlayer(player);
        prepareActivePlayer(player, type);
        Map.Entry<String, Location> spawnEntry = map.spawns().isEmpty() ? null : map.spawns().entrySet().iterator().next();
        Location spawn = spawnEntry == null ? player.getLocation() : spawnEntry.getValue();
        session.teams().put(player.getUniqueId(), teamFromSpawnKey(spawnEntry == null ? "" : spawnEntry.getKey(), 0));
        allowTeleport(player.getUniqueId());
        TeleportService.teleport(plugin, player, spawn, "quick test spawn");
        statsService.recordParticipation(player.getUniqueId(), type);
        return true;
    }

    public boolean join(Player player) {
        if (session == null || (session.phase() != EventPhase.VOTE
                && session.phase() != EventPhase.JOIN
                && session.phase() != EventPhase.COUNTDOWN)) {
            return false;
        }
        if (isEventPlayer(player.getUniqueId())) {
            plugin.messages().send(player, "event-already-joined");
            return true;
        }
        snapshotService.capture(player);
        session.participants().add(player.getUniqueId());
        markEventPlayer(player);
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.updateInventory();
        kitVotingItemsHandler.accept(player);
        Location waitingHub = session.waitingHub();
        if (waitingHub != null) {
            allowTeleport(player.getUniqueId());
            TeleportService.teleport(plugin, player, waitingHub, "event waiting hub");
        }
        plugin.messages().send(player, "event-joined");
        notifySessionPlayers("event-player-joined", player.getName(), player.getUniqueId());
        playConfiguredSound(player, "sounds.event-join");
        maybeApplyReadyCountdown();
        return true;
    }

    public boolean spectate(Player player) {
        if (session == null || (session.phase() != EventPhase.ACTIVE && session.phase() != EventPhase.TROPHY)) {
            return false;
        }
        EventMap map = session.selectedMap();
        if (map == null || map.spectatorSpawn() == null) {
            return false;
        }
        if (!isEventPlayer(player.getUniqueId())) {
            snapshotService.capture(player);
        }
        session.participants().remove(player.getUniqueId());
        session.spectators().add(player.getUniqueId());
        markEventPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        allowTeleport(player.getUniqueId());
        TeleportService.teleport(plugin, player, map.spectatorSpawn(), "event spectator spawn");
        plugin.messages().send(player, "event-spectating");
        notifySessionPlayers("event-player-spectating", player.getName(), player.getUniqueId());
        return true;
    }

    public boolean leave(Player player) {
        if (session == null) {
            return false;
        }
        session.participants().remove(player.getUniqueId());
        session.spectators().remove(player.getUniqueId());
        playerVotes.remove(player.getUniqueId());
        kitService.clearSelection(player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        allowTeleport(player.getUniqueId());
        cleanupEventInventory(player);
        snapshotService.restore(player, false);
        restoreScoreboard(player);
        unmarkEventPlayer(player);
        plugin.messages().send(player, "event-left");
        notifySessionPlayers("event-player-left", player.getName(), player.getUniqueId());
        if (session != null && session.phase() == EventPhase.ACTIVE && session.participants().isEmpty()) {
            scheduleEndActiveEvent(List.copyOf(session.finalRankings()), 0L);
        }
        return true;
    }

    public void finishParticipant(Player player) {
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().remove(player.getUniqueId())) {
            return;
        }
        cleanupBoatRacePlayer(player.getUniqueId());
        if (!session.finalRankings().contains(player.getUniqueId())) {
            session.finalRankings().add(player.getUniqueId());
        }
        messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + player.getName() + " finished.");
        session.spectators().add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        EventMap map = session.selectedMap();
        if (map != null && map.spectatorSpawn() != null) {
            allowTeleport(player.getUniqueId());
            TeleportService.teleport(plugin, player, map.spectatorSpawn(), "finished spectator spawn");
        }
        if (session.participants().isEmpty()) {
            scheduleEndActiveEvent(List.copyOf(session.finalRankings()), 20L);
        }
    }

    public void eliminateParticipant(Player player, String reason) {
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().remove(player.getUniqueId())) {
            return;
        }
        cleanupBoatRacePlayer(player.getUniqueId());
        session.spectators().add(player.getUniqueId());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.SPECTATOR);
        EventMap map = session.selectedMap();
        if (map != null && map.spectatorSpawn() != null) {
            allowTeleport(player.getUniqueId());
            TeleportService.teleport(plugin, player, map.spectatorSpawn(), "eliminated spectator spawn");
        }
        if (session.definition().type() == EventType.BLOCK_PARTY) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You were eliminated " + reason + ".");
            messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + player.getName() + " was eliminated.");
        } else if (!(session.definition().type() == EventType.BLOCK_PARTY && "stood on the wrong color".equalsIgnoreCase(reason))) {
            plugin.messages().send(player, "event-eliminated", Map.of("reason", reason));
        }
        if (session.participants().size() <= 1) {
            scheduleEndActiveEvent(List.copyOf(session.participants()), 20L);
        }
    }

    public void messageEventPlayers(String message) {
        if (session == null) {
            return;
        }
        for (UUID uuid : eventPlayers()) {
            Player eventPlayer = Bukkit.getPlayer(uuid);
            if (eventPlayer != null) {
                eventPlayer.sendMessage(message);
            }
        }
    }

    public List<UUID> activeParticipants() {
        if (session == null) {
            return List.of();
        }
        return List.copyOf(session.participants());
    }

    public boolean restoreSnapshot(Player player) {
        if (session != null) {
            session.participants().remove(player.getUniqueId());
            session.spectators().remove(player.getUniqueId());
        }
        playerVotes.remove(player.getUniqueId());
        kitService.clearSelection(player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        allowTeleport(player.getUniqueId());
        boolean restored = snapshotService.restore(player, false);
        if (restored) {
            restoreScoreboard(player);
            unmarkEventPlayer(player);
        }
        if (!restored) {
            allowedTeleports.remove(player.getUniqueId());
        }
        return restored;
    }

    public int retryPendingOnlineRestores() {
        return snapshotService.retryPendingOnlineRestores();
    }

    public void handleQuit(Player player) {
        if (session == null) {
            return;
        }
        boolean wasParticipant = session.participants().remove(player.getUniqueId());
        session.spectators().remove(player.getUniqueId());
        playerVotes.remove(player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        kitService.clearSelection(player.getUniqueId());
        allowedTeleports.remove(player.getUniqueId());
        spawnLocked.remove(player.getUniqueId());
        restoreScoreboard(player);
        unmarkEventPlayer(player);
        if (wasParticipant && session.phase() == EventPhase.ACTIVE) {
            if (session.participants().isEmpty()) {
                scheduleEndActiveEvent(List.copyOf(session.finalRankings()), 0L);
            } else if (isLastPlayerStandingEvent(session.definition().type()) && session.participants().size() <= 1) {
                scheduleEndActiveEvent(List.copyOf(session.participants()), 20L);
            }
        }
    }

    public void handleJoin(Player player) {
        if (snapshotService.hasUnrestoredSnapshot(player.getUniqueId()) && !isEventPlayer(player.getUniqueId())) {
            allowTeleport(player.getUniqueId());
            if (snapshotService.restore(player, false)) {
                plugin.messages().send(player, "event-restored-on-join");
            }
        }
    }

    public void castVote(Player player, EventType type) {
        if (session == null || session.phase() != EventPhase.VOTE || !session.votes().containsKey(type)) {
            return;
        }
        EventType previous = playerVotes.put(player.getUniqueId(), type);
        if (previous != null) {
            session.votes().computeIfPresent(previous, (key, value) -> Math.max(0, value - 1));
        }
        session.votes().computeIfPresent(type, (key, value) -> value + 1);
        playConfiguredSound(player, "sounds.vote-cast");
    }

    public EventSession session() {
        return session;
    }

    public Map<EventType, Integer> liveVotes() {
        return session == null ? Map.of() : new HashMap<>(session.votes());
    }

    public void endActiveEvent(List<UUID> rankedPlayers) {
        if (session == null) {
            return;
        }
        session.finalRankings().clear();
        session.finalRankings().addAll(rankedPlayers);
        session.phase(EventPhase.TROPHY);
        if (boatRaceService != null) {
            boatRaceService.cleanupAll();
        }
        Location trophyRoom = session.trophyRoom();
        if (trophyRoom != null) {
            for (UUID uuid : allEventPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    allowTeleport(uuid);
                    TeleportService.teleport(plugin, player, trophyRoom, "event trophy room");
                }
            }
        }
        if (arenaResetService != null) {
            arenaResetService.reset();
        }
        if (podiumService != null) {
            podiumService.show(rankedPlayers);
        }
        if (!rankedPlayers.isEmpty()) {
            UUID winner = rankedPlayers.getFirst();
            statsService.recordWin(winner, session.definition().type());
            for (UUID uuid : session.participants()) {
                if (!uuid.equals(winner)) {
                    statsService.recordLoss(uuid, session.definition().type());
                }
            }
        }
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
        spawnLocked.clear();
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin, this::finishSession,
                plugin.getConfig().getLong("schedule.trophy-room-seconds", 10L) * 20L);
    }

    private void scheduleEndActiveEvent(List<UUID> rankedPlayers, long delayTicks) {
        if (phaseTask != null) {
            return;
        }
        cancelActiveTask();
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                phaseTask = null;
                if (session != null && session.phase() == EventPhase.ACTIVE) {
                    endActiveEvent(rankedPlayers);
                }
            }
        }, delayTicks);
    }

    public void stop(String reason) {
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
        spawnLocked.clear();
        if (session != null) {
            restoreAll(session.participants());
            restoreAll(session.spectators());
            resetRuntimeServices();
            Bukkit.broadcastMessage(plugin.messages().format("event-ended-no-winner", Map.of("event", session.definition().displayName())));
            session = null;
        }
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        kitService.clearSelections();
    }

    public boolean advancePhase() {
        if (session == null) {
            return false;
        }
        if (session.phase() == EventPhase.VOTE) {
            cancelTask();
            advanceToJoin();
            return true;
        }
        if (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN) {
            cancelTask();
            cancelPreStartTask();
            session.adminStarted(true);
            return beginActivePhase();
        }
        if (session.phase() == EventPhase.TROPHY) {
            cancelTask();
            cancelActiveTask();
            cancelPreStartTask();
            finishSession();
            return true;
        }
        return false;
    }

    public void queue(EventDefinition definition) {
        queue.add(definition);
    }

    public boolean isEventPlayer(UUID uuid) {
        return session != null && (session.participants().contains(uuid) || session.spectators().contains(uuid));
    }

    public boolean isSpawnLocked(UUID uuid) {
        return spawnLocked.contains(uuid);
    }

    public boolean isWaitingLocked() {
        return session != null && (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN)
                && plugin.getConfig().getBoolean("restrictions.lock-waiting-hub", true);
    }

    public boolean isTrophyLocked() {
        return session != null && session.phase() == EventPhase.TROPHY
                && plugin.getConfig().getBoolean("restrictions.lock-trophy-room", true);
    }

    public EventMap activeMap() {
        return session == null ? null : session.selectedMap();
    }

    public Set<UUID> eventPlayers() {
        return allEventPlayers();
    }

    public String teamFor(UUID uuid) {
        if (session == null) {
            return "";
        }
        return session.teams().getOrDefault(uuid, "");
    }

    public String runtimeScoreboardValue(String key, String fallback) {
        return runtimeScoreboardValues.getOrDefault(key, fallback);
    }

    public void setRuntimeScoreboardValue(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        runtimeScoreboardValues.put(key, value == null ? "" : value);
    }

    public long activeSecondsRemaining() {
        if (session == null || session.phase() != EventPhase.ACTIVE || activeEndsAtMillis <= 0L) {
            return 0L;
        }
        long remaining = activeEndsAtMillis - System.currentTimeMillis();
        return remaining <= 0L ? 0L : (long) Math.ceil(remaining / 1000.0D);
    }

    public Location waitingHubLocation() {
        return configuredLocation("locations.waiting-hub");
    }

    public Location trophyRoomLocation() {
        return configuredLocation("locations.trophy-room");
    }

    public CuboidRegion waitingHubRegion() {
        return configuredRegion("locations.waiting-hub-region");
    }

    public CuboidRegion trophyRoomRegion() {
        return configuredRegion("locations.trophy-room-region");
    }

    public boolean canExitByTeleport(UUID uuid) {
        Long expiresAt = allowedTeleports.get(uuid);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            allowedTeleports.remove(uuid);
            return false;
        }
        return true;
    }

    public String nextHourlyVoteLabel() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        return next.toString().replace('T', ' ');
    }

    public long minutesUntilNextHour() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.plusHours(1).truncatedTo(ChronoUnit.HOURS)).toMinutes();
    }

    public void shutdown() {
        stop("shutdown");
    }

    private void createVoteSession(List<EventDefinition> choices, String startedBy, boolean adminStarted) {
        runtimeScoreboardValues.clear();
        session = new EventSession(choices.getFirst(), EventPhase.VOTE);
        session.startedBy(startedBy);
        session.adminStarted(adminStarted);
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        choices.forEach(choice -> session.votes().put(choice.type(), 0));
    }

    private List<EventDefinition> randomAvailableChoices() {
        List<EventDefinition> pool = new ArrayList<>(availableStartChoices());
        List<EventDefinition> out = new ArrayList<>();
        int amount = plugin.getConfig().getInt("events.vote-options", 5);
        while (!pool.isEmpty() && out.size() < amount) {
            out.add(pool.remove(ThreadLocalRandom.current().nextInt(pool.size())));
        }
        return out;
    }

    private List<EventDefinition> validChoice(EventDefinition definition) {
        return mapSetupService.usableMapsFor(definition.type()).isEmpty() ? List.of() : List.of(definition);
    }

    private void startVoteCountdown() {
        cancelTask();
        voteRemaining = plugin.getConfig().getInt("schedule.vote-phase-seconds", 60);
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (this) {
                if (session == null || session.phase() != EventPhase.VOTE) {
                    cancelTask();
                    return;
                }
                if (voteRemaining <= 0) {
                    cancelTask();
                    advanceToJoin();
                    return;
                }
                if (voteRemaining == 30) {
                    Bukkit.broadcastMessage(plugin.messages().format("vote-reminder", Map.of("time", formatDuration(voteRemaining))));
                }
                voteRemaining--;
            }
        }, 20L, 20L);
    }

    private void advanceToJoin() {
        if (session == null) {
            return;
        }
        EventType winner = session.votes().entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(session.definition().type());
        EventDefinition definition = registry.definition(winner);
        session.definition(definition == null ? session.definition() : definition);
        Bukkit.broadcastMessage(plugin.messages().format("vote-winner", Map.of("event", session.definition().displayName())));
        voteCloseHandler.run();
        session.phase(EventPhase.JOIN);
        selectMapForSession();
        broadcastJoinPrompt(session.definition().displayName(), "vote");
        startJoinCountdown();
    }

    private void startJoinCountdown() {
        cancelTask();
        if (session.selectedMap() == null) {
            cancelForNotEnoughPlayers();
            return;
        }
        readyCountdownApplied = false;
        countdownRemaining = plugin.getConfig().getInt("schedule.join-phase-seconds", 300);
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickJoinCountdown, 20L, 20L);
    }

    private void tickJoinCountdown() {
        if (session == null || (session.phase() != EventPhase.JOIN && session.phase() != EventPhase.COUNTDOWN)) {
            cancelTask();
            return;
        }
        maybeApplyReadyCountdown();
        if (countdownRemaining <= 0) {
            if (!hasEnoughPlayersForStart()) {
                cancelForNotEnoughPlayers();
                return;
            }
            beginActivePhase();
            return;
        }
        if (shouldAnnounceCountdown(countdownRemaining)) {
            Bukkit.broadcastMessage(plugin.messages().format("countdown-tick", Map.of(
                    "event", session.definition().displayName(),
                    "seconds", String.valueOf(countdownRemaining),
                    "time", formatDuration(countdownRemaining),
                    "players", String.valueOf(session.participants().size()),
                "min", String.valueOf(minPlayers(session.definition().type()))
            )));
        }
        updateCountdownExperience(countdownRemaining, plugin.getConfig().getInt("schedule.join-phase-seconds", 300));
        countdownRemaining--;
    }

    private void maybeApplyReadyCountdown() {
        if (session == null || readyCountdownApplied) {
            return;
        }
        int requiredReadyPlayers = session.adminStarted() ? 1 : readyPlayers(session.definition().type());
        if (session.participants().size() < requiredReadyPlayers) {
            return;
        }
        int readySeconds = plugin.getConfig().getInt("schedule.ready-countdown-seconds", 60);
        if (countdownRemaining > readySeconds) {
            countdownRemaining = readySeconds;
        }
        readyCountdownApplied = true;
        session.phase(EventPhase.COUNTDOWN);
        Bukkit.broadcastMessage(plugin.messages().format("join-countdown-ready", Map.of(
                "event", session.definition().displayName(),
                "seconds", String.valueOf(countdownRemaining),
                "time", formatDuration(countdownRemaining)
        )));
    }

    private boolean beginActivePhase() {
        if (session == null) {
            return false;
        }
        if (!hasEnoughPlayersForStart()) {
            cancelForNotEnoughPlayers();
            return false;
        }
        cancelTask();
        cancelPreStartTask();
        session.phase(EventPhase.PRESTART);
        EventSession startingSession = session;
        spawnLocked.clear();
        session.teams().clear();
        EventMap map = session.selectedMap();
        if ((session.definition().type() == EventType.BOAT_RACE || session.definition().type() == EventType.PARKOUR
                || session.definition().type() == EventType.HORSE_RACE)
                && boatRaceService != null) {
            boatRaceService.preparePreStart(map);
        }
        if (session.definition().type() == EventType.BLOCK_PARTY) {
            prepareBlockPartyFloor(map);
        }
        if (map != null && !map.spawns().isEmpty()) {
            List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
            List<Map.Entry<String, Location>> spawns = List.copyOf(map.spawns().entrySet());
            int index = 0;
            for (UUID uuid : session.participants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    Map.Entry<String, Location> spawn = spawns.get(index % spawns.size());
                    prepareActivePlayer(player, session.definition().type());
                    session.teams().put(uuid, teamFromSpawnKey(spawn.getKey(), index));
                    allowTeleport(uuid);
                    teleports.add(TeleportService.teleport(plugin, player, spawn.getValue(), "event spawn").thenApply(success -> {
                        if (success && session != null && session.definition().type() == EventType.BOAT_RACE && boatRaceService != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> boatRaceService.mountPlayer(player, spawn.getValue()));
                        }
                        if (success && session != null && session.definition().type() == EventType.HORSE_RACE && boatRaceService != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> boatRaceService.mountHorse(player, spawn.getValue()));
                        }
                        return success;
                    }));
                    if (!canMoveDuringPreStart(session.definition().type())) {
                        spawnLocked.add(uuid);
                    }
                }
                index++;
            }
            if (!teleports.isEmpty()) {
                CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            synchronized (this) {
                                if (session == startingSession && session != null && session.phase() == EventPhase.PRESTART) {
                                    startPreStartCountdown();
                                }
                            }
                        }));
                return true;
            }
        }
        startPreStartCountdown();
        return true;
    }

    private void startPreStartCountdown() {
        preStartRemaining = 5;
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (this) {
                if (session == null || session.phase() != EventPhase.PRESTART) {
                    cancelPreStartTask();
                    return;
                }
                if (preStartRemaining <= 0) {
                    releaseActivePlayers();
                    return;
                }
                messageEventPlayers(plugin.messages().format("prestart-countdown", Map.of(
                        "event", session.definition().displayName(),
                        "seconds", String.valueOf(preStartRemaining)
                )));
                updateCountdownExperience(preStartRemaining, 5);
                playPreStartCountdownSound(preStartRemaining);
                preStartRemaining--;
            }
        }, 0L, 20L);
        preStartTask = phaseTask;
    }

    private void playPreStartCountdownSound(int seconds) {
        if (session == null) {
            return;
        }
        if (seconds > 3) {
            return;
        }
        Sound sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        float pitch = 1.0F;
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, 0.9F, pitch);
            }
        }
    }

    private void releaseActivePlayers() {
        if (session == null) {
            return;
        }
        cancelPreStartTask();
        spawnLocked.clear();
        session.phase(EventPhase.ACTIVE);
        clearCountdownExperience();
        playGoSound();
        if ((session.definition().type() == EventType.BOAT_RACE || session.definition().type() == EventType.PARKOUR
                || session.definition().type() == EventType.HORSE_RACE)
                && boatRaceService != null) {
            boatRaceService.releaseWall(session.selectedMap());
        }
        populateMapContainers(session.selectedMap(), session.definition().type());
        for (UUID uuid : session.participants()) {
            statsService.recordParticipation(uuid, session.definition().type());
        }
        messageEventPlayers(plugin.messages().format("event-active-started", Map.of("event", session.definition().displayName())));
        scheduleActiveTimer(session.definition().type());
    }

    private void updateCountdownExperience(int seconds, int totalSeconds) {
        if (session == null) {
            return;
        }
        float progress = totalSeconds <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, seconds / (float) totalSeconds));
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setLevel(Math.max(0, seconds));
                player.setExp(progress);
            }
        }
    }

    private void clearCountdownExperience() {
        if (session == null || session.definition().type() == EventType.BLOCK_PARTY) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setLevel(0);
                player.setExp(0.0F);
            }
        }
    }

    private void playGoSound() {
        if (session == null) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.6F);
            }
        }
    }

    private void cancelForNotEnoughPlayers() {
        if (session == null) {
            return;
        }
        Bukkit.broadcastMessage(plugin.messages().format("event-cancelled-not-enough", Map.of(
                "event", session.definition().displayName(),
                "players", String.valueOf(session.participants().size()),
                "min", String.valueOf(minPlayers(session.definition().type()))
        )));
        restoreAll(session.participants());
        restoreAll(session.spectators());
        session = null;
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        spawnLocked.clear();
        kitService.clearSelections();
        resetRuntimeServices();
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
    }

    private boolean hasEnoughPlayersForStart() {
        if (session == null) {
            return false;
        }
        int required = session.adminStarted() ? 1 : minPlayers(session.definition().type());
        return session.participants().size() >= required;
    }

    private int minPlayers(EventType type) {
        return eventConfigService.minPlayers(type, plugin.getConfig().getInt("events.min-players", 2));
    }

    private int readyPlayers(EventType type) {
        return eventConfigService.readyPlayers(type, plugin.getConfig().getInt("events.ready-players", minPlayers(type)));
    }

    private boolean shouldAnnounceCountdown(int seconds) {
        if (seconds > 60) {
            return seconds % 60 == 0;
        }
        return seconds == 60 || seconds == 30 || seconds == 15 || seconds == 5;
    }

    private String formatDuration(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        if (seconds > 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + " second" + (seconds == 1 ? "" : "s");
    }

    private void selectMapForSession() {
        if (session == null || session.selectedMap() != null) {
            return;
        }
        List<EventMap> maps = mapSetupService.usableMapsFor(session.definition().type());
        if (!maps.isEmpty()) {
            session.selectedMap(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        }
    }

    private List<UUID> rankedByRuntimeScore(String prefix) {
        if (session == null) {
            return List.of();
        }
        return session.participants().stream()
                .sorted((left, right) -> Integer.compare(runtimeScore(prefix, right), runtimeScore(prefix, left)))
                .toList();
    }

    private int runtimeScore(String prefix, UUID uuid) {
        String value = runtimeScoreboardValues.get(prefix + uuid);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void prepareBlockPartyFloor(EventMap map) {
        CuboidRegion area = map == null ? null : map.areas().get("color-floor");
        if (area == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        Material[] colors = {
                Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE,
                Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
                Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE
        };
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized Block Party prestart floor update (" + blocks + " blocks). Resize the color floor.");
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(colors[random.nextInt(colors.length)], false);
                }
            }
        }
    }

    private void finishSession() {
        if (session == null) {
            return;
        }
        EventSession finished = session;
        if (!finished.finalRankings().isEmpty()) {
            payWinner(finished);
        }
        restoreAll(finished.participants());
        restoreAll(finished.spectators());
        resetRuntimeServices();
        if (finished.finalRankings().isEmpty()) {
            Bukkit.broadcastMessage(plugin.messages().format("event-ended-no-winner", Map.of("event", finished.definition().displayName())));
        } else {
            Bukkit.broadcastMessage(plugin.messages().format("event-ended", Map.of(
                    "event", finished.definition().displayName(),
                    "winners", winnerNames(finished.finalRankings())
            )));
        }
        session = null;
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        spawnLocked.clear();
        kitService.clearSelections();
        cancelActiveTask();
        cancelPreStartTask();
        EventDefinition queued = queue.poll();
        if (queued != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> startManualVote(Bukkit.getConsoleSender(), queued, false),
                    plugin.getConfig().getLong("schedule.queue-delay-seconds", 30L) * 20L);
        }
    }

    private void restoreAll(Set<UUID> uuids) {
        for (UUID uuid : List.copyOf(uuids)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                allowTeleport(uuid);
                cleanupEventInventory(player);
                snapshotService.restore(player, false);
                restoreScoreboard(player);
                unmarkEventPlayer(player);
            }
        }
    }

    private void payWinner(EventSession finished) {
        if (finished.finalRankings().isEmpty() || economy == null) {
            return;
        }
        double reward = eventConfigService.winnerReward(finished.definition().type(),
                plugin.getConfig().getDouble("economy.winner-reward", 100.0D));
        if (reward <= 0.0D) {
            return;
        }
        List<UUID> winners = finished.definition().type() == EventType.CAPTURE_THE_FLAG
                ? finished.finalRankings()
                : List.of(finished.finalRankings().getFirst());
        for (UUID winner : winners) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(winner);
            economy.depositPlayer(offlinePlayer, reward);
            Player online = offlinePlayer.getPlayer();
            if (online != null) {
                plugin.messages().send(online, "event-winner-paid", Map.of("amount", String.valueOf(reward)));
            }
        }
    }

    private String winnerNames(List<UUID> rankings) {
        int limit = session != null && session.definition().type() == EventType.CAPTURE_THE_FLAG ? Integer.MAX_VALUE : 1;
        return rankings.stream()
                .limit(limit)
                .map(uuid -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    return player.getName() == null ? uuid.toString() : player.getName();
                })
                .collect(Collectors.joining(", "));
    }

    private Set<UUID> allEventPlayers() {
        if (session == null) {
            return Set.of();
        }
        Set<UUID> players = new java.util.LinkedHashSet<>(session.participants());
        players.addAll(session.spectators());
        return players;
    }

    private void notifySessionPlayers(String messageKey, String playerName, UUID exclude) {
        if (session == null) {
            return;
        }
        for (UUID uuid : allEventPlayers()) {
            if (uuid.equals(exclude)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, messageKey, Map.of("player", playerName));
            }
        }
    }

    private void cancelTask() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }
    }

    private void cancelPreStartTask() {
        if (preStartTask != null) {
            preStartTask.cancel();
            if (phaseTask == preStartTask) {
                phaseTask = null;
            }
            preStartTask = null;
        }
    }

    private void scheduleActiveTimer(EventType type) {
        cancelActiveTask();
        long seconds = eventConfigService.activePhaseSeconds(type,
                plugin.getConfig().getLong("schedule.active-phase-seconds", 600L));
        if (seconds <= 0L) {
            activeEndsAtMillis = 0L;
            return;
        }
        activeEndsAtMillis = System.currentTimeMillis() + (seconds * 1000L);
        activeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                if (session != null && session.phase() == EventPhase.ACTIVE) {
                    if (session.definition().type() == EventType.QUAKE) {
                        endActiveEvent(rankedByRuntimeScore("quake-score-"));
                    } else {
                        endActiveEvent(List.copyOf(session.finalRankings()));
                    }
                }
            }
        }, seconds * 20L);
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        activeEndsAtMillis = 0L;
    }

    private void broadcastVotePrompt() {
        Bukkit.broadcast(Component.text("[Events] ", NamedTextColor.GOLD)
                .append(Component.text("An event is starting soon. ", NamedTextColor.YELLOW))
                .append(Component.text("Click Here", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/event vote")))
                .append(Component.text(" or type /event vote to vote on what event to play next.", NamedTextColor.GRAY)));
    }

    private void broadcastJoinPrompt(String eventName, String source) {
        String sourceText = source.equals("vote") ? "The vote chose " + eventName + ". " : eventName + " is open. ";
        Bukkit.broadcast(Component.text("[Events] ", NamedTextColor.GOLD)
                .append(Component.text(sourceText, NamedTextColor.YELLOW))
                .append(Component.text("Click to join", NamedTextColor.GREEN).decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/event join")))
                .append(Component.text(" or type /event join.", NamedTextColor.GRAY)));
    }

    private void allowTeleport(UUID uuid) {
        allowedTeleports.put(uuid, System.currentTimeMillis() + 10_000L);
    }

    private void markEventPlayer(Player player) {
        player.addScoreboardTag(EVENT_SCOREBOARD_TAG);
        player.addScoreboardTag(EVENT_SCOREBOARD_TAG + "_" + session.definition().type().name().toLowerCase(Locale.ROOT));
        player.setMetadata(EVENT_METADATA_KEY, new FixedMetadataValue(plugin, true));
        player.setMetadata(EVENT_METADATA_KEY + "_type", new FixedMetadataValue(plugin, session.definition().type().name()));
    }

    private void unmarkEventPlayer(Player player) {
        player.removeScoreboardTag(EVENT_SCOREBOARD_TAG);
        for (EventType type : EventType.values()) {
            player.removeScoreboardTag(EVENT_SCOREBOARD_TAG + "_" + type.name().toLowerCase(Locale.ROOT));
        }
        player.removeMetadata(EVENT_METADATA_KEY, plugin);
        player.removeMetadata(EVENT_METADATA_KEY + "_type", plugin);
    }

    private void prepareActivePlayer(Player player, EventType type) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        switch (type) {
            case PARKOUR, CAPTURE_PLAYERS, BLOCK_PARTY, HOT_POTATO, RED_LIGHT_GREEN_LIGHT -> player.setGameMode(GameMode.ADVENTURE);
            case CAPTURE_THE_FLAG -> {
                player.setGameMode(GameMode.SURVIVAL);
                equipArmor(player, Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS);
                player.getInventory().addItem(namedItem(Material.IRON_SWORD, "CTF Sword"));
                player.getInventory().addItem(namedItem(Material.IRON_AXE, "CTF Axe"));
                player.getInventory().addItem(namedItem(Material.BOW, "CTF Bow"));
                player.getInventory().setItemInOffHand(namedItem(Material.SHIELD, "CTF Shield"));
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 24));
                player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
            }
            case BOAT_RACE -> {
                player.setGameMode(GameMode.SURVIVAL);
            }
            case HORSE_RACE -> {
                player.setGameMode(GameMode.SURVIVAL);
            }
            case ELYTRA_RACE -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().setChestplate(namedItem(Material.ELYTRA, "Race Elytra"));
                player.getInventory().addItem(namedItem(Material.FIREWORK_ROCKET, "Race Rockets", 16));
                player.getInventory().addItem(namedItem(Material.RECOVERY_COMPASS, "Checkpoint Return"));
            }
            case SPLEEF -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.DIAMOND_SHOVEL, "Spleef Shovel"));
            }
            case SPLEEG -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.DIAMOND_SHOVEL, "Spleeg Shovel"));
                player.getInventory().addItem(namedItem(Material.SNOWBALL, "Spleeg Snowballs", 64));
                player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 64));
            }
            case SUMO_1V1, SUMO_2V2, SUMO_FFA -> {
                player.setGameMode(GameMode.ADVENTURE);
                player.getInventory().addItem(namedItem(Material.STICK, "Sumo Stick"));
            }
            case KNOCKBACK_FFA -> {
                player.setGameMode(GameMode.ADVENTURE);
                ItemStack stick = namedItem(Material.STICK, "Knockback Stick");
                stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 4);
                player.getInventory().addItem(stick);
            }
            case QUAKE -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.GOLDEN_HOE, "Quake Railgun"));
            }
            case FIGHT_1V1, FIGHT_2V2, FIGHT_FFA -> {
                player.setGameMode(GameMode.SURVIVAL);
                if (kitService.selectedKit(player).isPresent()) {
                    kitService.applySelectedKit(player);
                } else {
                    equipArmor(player, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
                    player.getInventory().addItem(namedItem(Material.IRON_SWORD, "Event Sword"));
                    player.getInventory().addItem(namedItem(Material.BOW, "Event Bow"));
                    player.getInventory().addItem(new ItemStack(Material.ARROW, 16));
                    player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
                }
            }
            case ONE_IN_THE_CHAMBER -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.BOW, "One Shot Bow"));
                player.getInventory().addItem(namedItem(Material.WOODEN_SWORD, "Backup Sword"));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            }
            case SKYWARS -> {
                player.setGameMode(GameMode.SURVIVAL);
            }
            case BEDWARS -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.WOODEN_SWORD, "BedWars Sword"));
                player.getInventory().addItem(new ItemStack(Material.WHITE_WOOL, 32));
            }
            default -> player.setGameMode(GameMode.SURVIVAL);
        }
        player.updateInventory();
    }

    private ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, 1);
    }

    private ItemStack namedItem(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material);
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void equipArmor(Player player, Material helmet, Material chestplate, Material leggings, Material boots) {
        player.getInventory().setHelmet(new ItemStack(helmet));
        player.getInventory().setChestplate(new ItemStack(chestplate));
        player.getInventory().setLeggings(new ItemStack(leggings));
        player.getInventory().setBoots(new ItemStack(boots));
    }

    private void populateMapContainers(EventMap map, EventType type) {
        if (map == null || (type != EventType.SKYWARS && type != EventType.BEDWARS)) {
            return;
        }
        for (Map.Entry<Integer, List<Location>> entry : map.chests().entrySet()) {
            for (Location location : entry.getValue()) {
                if (location == null || location.getWorld() == null) {
                    continue;
                }
                BlockState state = location.getBlock().getState();
                if (!(state instanceof Container container)) {
                    continue;
                }
                Inventory inventory = container.getInventory();
                inventory.clear();
                placeLootRandomly(inventory, lootTableService.roll(type, entry.getKey()));
            }
        }
    }

    private void placeLootRandomly(Inventory inventory, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            stacks.addAll(splitLootStack(item, random));
        }
        Collections.shuffle(stacks);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots);
        int slotIndex = 0;
        for (ItemStack stack : stacks) {
            while (slotIndex < slots.size() && inventory.getItem(slots.get(slotIndex)) != null) {
                slotIndex++;
            }
            if (slotIndex >= slots.size()) {
                inventory.addItem(stack);
                continue;
            }
            inventory.setItem(slots.get(slotIndex++), stack);
        }
    }

    private List<ItemStack> splitLootStack(ItemStack item, ThreadLocalRandom random) {
        int amount = item.getAmount();
        int maxStack = item.getMaxStackSize();
        if (amount <= 1 || maxStack <= 1) {
            return List.of(item);
        }
        int pieces = Math.min(amount, random.nextInt(2, Math.min(4, amount) + 1));
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (int i = pieces; i > 0; i--) {
            int next = i == 1 ? remaining : random.nextInt(1, Math.max(2, remaining - i + 2));
            ItemStack split = item.clone();
            split.setAmount(next);
            result.add(split);
            remaining -= next;
        }
        return result;
    }

    private void cleanupEventInventory(Player player) {
        player.getInventory().remove(Material.ENDER_PEARL);
        player.updateInventory();
    }

    private void restoreScoreboard(Player player) {
        if (scoreboardService != null) {
            scoreboardService.restore(player);
        }
    }

    private void resetRuntimeServices() {
        if (arenaResetService != null) {
            arenaResetService.reset();
        }
        if (podiumService != null) {
            podiumService.clear();
        }
        if (boatRaceService != null) {
            boatRaceService.cleanupAll();
        }
    }

    private boolean isLastPlayerStandingEvent(EventType type) {
        return type == EventType.SKYWARS
                || type == EventType.BEDWARS
                || type == EventType.FIGHT_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.FIGHT_FFA
                || type == EventType.SUMO_1V1
                || type == EventType.SUMO_2V2
                || type == EventType.SUMO_FFA
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.ONE_IN_THE_CHAMBER
                || type == EventType.BLOCK_PARTY
                || type == EventType.HOT_POTATO
                || type == EventType.SPLEEF
                || type == EventType.SPLEEG
                || type == EventType.RED_LIGHT_GREEN_LIGHT;
    }

    private boolean canMoveDuringPreStart(EventType type) {
        return type == EventType.FIGHT_FFA
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.QUAKE
                || type == EventType.BOAT_RACE
                || type == EventType.HORSE_RACE
                || type == EventType.PARKOUR
                || type == EventType.BLOCK_PARTY
                || type == EventType.SPLEEF
                || type == EventType.SPLEEG;
    }

    private void cleanupBoatRacePlayer(UUID uuid) {
        if (boatRaceService != null) {
            boatRaceService.cleanupPlayer(uuid);
        }
    }

    public void remountRaceVehicle(Player player, Location spawn) {
        if (session == null || boatRaceService == null || player == null || spawn == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (type == EventType.BOAT_RACE) {
                boatRaceService.mountPlayer(player, spawn);
            } else if (type == EventType.HORSE_RACE) {
                boatRaceService.mountHorse(player, spawn);
            }
        });
    }

    private String teamFromSpawnKey(String key, int fallbackIndex) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher namedMatcher = java.util.regex.Pattern.compile("team-(red|blue|green|yellow|orange|purple|cyan|white)").matcher(lower);
        if (namedMatcher.find()) {
            return namedMatcher.group(1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("team-(\\d+)").matcher(lower);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = java.util.regex.Pattern.compile("spawn-?(\\d+)").matcher(lower);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return String.valueOf(fallbackIndex + 1);
    }

    private Location configuredLocation(String path) {
        return LocationCodec.decode(plugin.getConfig().getString(path, ""));
    }

    private void playConfiguredSound(Player player, String path) {
        String soundName = plugin.getConfig().getString(path + ".sound", "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            float volume = (float) plugin.getConfig().getDouble(path + ".volume", 0.7D);
            float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 1.0D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid configured sound at " + path + ": " + soundName);
        }
    }

    private CuboidRegion configuredRegion(String path) {
        Location pos1 = LocationCodec.decode(plugin.getConfig().getString(path + ".pos1", ""));
        Location pos2 = LocationCodec.decode(plugin.getConfig().getString(path + ".pos2", ""));
        if (pos1 == null || pos2 == null) {
            return null;
        }
        try {
            return CuboidRegion.fromCorners(pos1, pos2);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid configured region at " + path + ": " + ex.getMessage());
            return null;
        }
    }
}
