package org.enthusia.events;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.enthusia.events.command.AdminCommand;
import org.enthusia.events.command.SetupCommand;
import org.enthusia.events.audit.EventSpecAuditRegistry;
import org.enthusia.events.chat.ChatEventService;
import org.enthusia.events.config.ConfigUpdater;
import org.enthusia.events.command.EventCommand;
import org.enthusia.events.command.UnifiedEventCommand;
import org.enthusia.events.config.EventConfigService;
import org.enthusia.events.config.Messages;
import org.enthusia.events.event.BoatRaceService;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventGameplayListener;
import org.enthusia.events.event.EventRestrictionsListener;
import org.enthusia.events.event.EventRegistry;
import org.enthusia.events.event.EventScheduler;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.ParticipantListener;
import org.enthusia.events.event.PlayerSnapshotService;
import org.enthusia.events.gui.EventVoteGui;
import org.enthusia.events.gui.RestoreConfirmGui;
import org.enthusia.events.integration.ExternalPluginIntegrationService;
import org.enthusia.events.kit.EventKitService;
import org.enthusia.events.kit.KitVoteListener;
import org.enthusia.events.loot.LootTableService;
import org.enthusia.events.placeholder.EventsPlaceholderExpansion;
import org.enthusia.events.reset.ArenaResetService;
import org.enthusia.events.scoreboard.EventScoreboardService;
import org.enthusia.events.setup.SetupListener;
import org.enthusia.events.setup.SetupWizard;
import org.enthusia.events.skin.SkinCache;
import org.enthusia.events.stats.EventStatsService;
import org.enthusia.events.trophy.PodiumService;

@SuppressWarnings({"PMD.SingularField", "PMD.AvoidFieldNameMatchingMethodName"})
public final class EnthusiaEventsPlugin extends JavaPlugin {

    private Messages messages;
    private Economy economy;
    private SkinCache skinCache;
    private EventConfigService eventConfigService;
    private EventRegistry eventRegistry;
    private PlayerSnapshotService snapshotService;
    private MapSetupService mapSetupService;
    private MapCopyService mapCopyService;
    private EventStatsService statsService;
    private EventKitService kitService;
    private LootTableService lootTableService;
    private EventScoreboardService scoreboardService;
    private ArenaResetService arenaResetService;
    private BoatRaceService boatRaceService;
    private PodiumService podiumService;
    private ChatEventService chatEventService;
    private EventSpecAuditRegistry specAuditRegistry;
    private EventManager eventManager;
    private EventScheduler scheduler;
    private EventVoteGui voteGui;
    private RestoreConfirmGui restoreConfirmGui;
    private EventGameplayListener gameplayListener;
    private SetupWizard setupWizard;

    @Override
    public void onEnable() {
        ConfigUpdater.mergeDefaults(this, "config.yml");
        ConfigUpdater.mergeDefaults(this, "messages.yml");
        reloadConfig();
        if (getConfig().getDouble("economy.manual-start-cost", 150.0D) == 1000.0D) {
            getConfig().set("economy.manual-start-cost", 150.0D);
            saveConfig();
        }
        if (getConfig().getInt("schedule.vote-phase-seconds", 60) == 30) {
            getConfig().set("schedule.vote-phase-seconds", 60);
            saveConfig();
        }
        java.util.List<String> blockedCommands = new java.util.ArrayList<>(getConfig().getStringList("restrictions.blocked-commands"));
        for (String command : java.util.List.of("accept", "tpaccept", "tpa", "tpahere", "tpask")) {
            if (blockedCommands.stream().noneMatch(existing -> existing.equalsIgnoreCase(command))) {
                blockedCommands.add(command);
            }
        }
        getConfig().set("restrictions.blocked-commands", blockedCommands);
        saveConfig();

        messages = new Messages(this);
        economy = resolveEconomy();
        skinCache = new SkinCache();
        eventConfigService = new EventConfigService(this);
        eventRegistry = new EventRegistry(this, eventConfigService);
        snapshotService = new PlayerSnapshotService(this);
        mapSetupService = new MapSetupService(this);
        mapCopyService = new MapCopyService(this, mapSetupService);
        statsService = new EventStatsService(this);
        kitService = new EventKitService(this);
        lootTableService = new LootTableService(this);
        eventManager = new EventManager(this, eventRegistry, snapshotService, statsService, economy, mapSetupService, eventConfigService, kitService, lootTableService);
        scoreboardService = new EventScoreboardService(this, eventManager);
        arenaResetService = new ArenaResetService(this, eventManager);
        boatRaceService = new BoatRaceService(this, eventManager);
        podiumService = new PodiumService(this);
        eventManager.services(scoreboardService, arenaResetService, podiumService, boatRaceService);
        voteGui = new EventVoteGui(this, eventManager);
        eventManager.voteCloseHandler(voteGui::closeVoteViews);
        KitVoteListener kitVoteListener = new KitVoteListener(this, eventManager, kitService);
        eventManager.kitVotingItemsHandler(kitVoteListener::giveVotingItems);
        restoreConfirmGui = new RestoreConfirmGui(this, eventManager);
        scheduler = new EventScheduler(this, eventManager);
        gameplayListener = new EventGameplayListener(this, eventManager);
        setupWizard = new SetupWizard(this, mapSetupService);
        chatEventService = new ChatEventService(this, economy);
        specAuditRegistry = new EventSpecAuditRegistry(this, eventRegistry, mapSetupService);

        AdminCommand adminCommand = new AdminCommand(this, eventManager, mapSetupService, setupWizard, mapCopyService, restoreConfirmGui, kitService);
        EventCommand eventCommand = new EventCommand(this, eventManager, statsService, voteGui);
        UnifiedEventCommand unifiedCommand = new UnifiedEventCommand(eventCommand, adminCommand);
        getCommand("event").setExecutor(unifiedCommand);
        getCommand("event").setTabCompleter(unifiedCommand);
        getCommand("ee").setExecutor(unifiedCommand);
        getCommand("ee").setTabCompleter(unifiedCommand);

        SetupCommand setupCommand = new SetupCommand(this, mapSetupService, setupWizard);
        getCommand("setup").setExecutor(setupCommand);
        getCommand("setup").setTabCompleter(setupCommand);

        new ExternalPluginIntegrationService(this, eventManager).register();
        Bukkit.getPluginManager().registerEvents(new ParticipantListener(eventManager, voteGui, restoreConfirmGui, skinCache), this);
        Bukkit.getPluginManager().registerEvents(new EventRestrictionsListener(this, eventManager, mapSetupService), this);
        Bukkit.getPluginManager().registerEvents(gameplayListener, this);
        Bukkit.getPluginManager().registerEvents(new SetupListener(setupWizard), this);
        Bukkit.getPluginManager().registerEvents(kitVoteListener, this);
        Bukkit.getPluginManager().registerEvents(arenaResetService, this);
        Bukkit.getPluginManager().registerEvents(boatRaceService, this);
        Bukkit.getPluginManager().registerEvents(chatEventService, this);
        Bukkit.getScheduler().runTaskTimer(this, setupWizard::tickVisuals, 13L, 13L);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EventsPlaceholderExpansion(this, statsService).register();
        }

        specAuditRegistry.logStartupWarnings();
        scoreboardService.start();
        chatEventService.start();
        scheduler.start();
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (chatEventService != null) {
            chatEventService.stop();
        }
        if (scoreboardService != null) {
            scoreboardService.stop();
        }
        if (gameplayListener != null) {
            gameplayListener.shutdown();
        }
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (statsService != null) {
            statsService.save();
        }
        if (snapshotService != null) {
            snapshotService.save();
        }
    }

    private Economy resolveEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    public Messages messages() {
        return messages;
    }

    public SkinCache skinCache() {
        return skinCache;
    }

    public EventRegistry eventRegistry() {
        return eventRegistry;
    }

    public void reloadPlugin() {
        reloadConfig();
        messages.reload();
        eventConfigService.reload();
        kitService.reload();
        lootTableService.reload();
        chatEventService.reload();
        scoreboardService.reload();
        eventRegistry.reload();
        mapSetupService.reload();
        specAuditRegistry.logStartupWarnings();
        scheduler.restart();
    }

    public void resetGeneratedConfigs() {
        backupAndRegenerate("config.yml");
        backupAndRegenerate("messages.yml");
        reloadPlugin();
    }

    public void resetLootConfig() {
        lootTableService.resetToDefaults();
    }

    private void backupAndRegenerate(String resourceName) {
        java.io.File file = new java.io.File(getDataFolder(), resourceName);
        if (file.exists()) {
            java.io.File backup = new java.io.File(getDataFolder(), resourceName + ".bak-" + System.currentTimeMillis());
            if (!file.renameTo(backup)) {
                getLogger().warning("Could not back up " + resourceName + " before reset.");
                return;
            }
        }
        saveResource(resourceName, false);
    }
}
