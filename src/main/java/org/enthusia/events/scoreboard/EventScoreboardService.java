package org.enthusia.events.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventScoreboardService {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final File file;
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> renderedEntries = new HashMap<>();
    private final List<UUID> tabScoreboardDisabled = new ArrayList<>();
    private String title = "&6&lEnthusia Events";
    private List<String> configuredLines = List.of(
            "&e{event}",
            "",
            "&7Phase: &f{phase}",
            "&7Players: &f{players}",
            "&7Spectators: &f{spectators}",
            "&7Teams: &f{teams}",
            "&7Finished: &f{finished}",
            "",
            "&8{map}"
    );
    private BukkitTask task;

    public EventScoreboardService(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        File directory = new File(plugin.getDataFolder(), "scoreboards");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create scoreboards folder.");
        }
        this.file = new File(directory, "scoreboards.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            writeDefaults();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        title = yaml.getString("active.title", title);
        configuredLines = yaml.getStringList("active.lines");
        if (configuredLines.isEmpty()) {
            configuredLines = List.of("&e{event}", "", "&7Phase: &f{phase}", "&7Players: &f{players}", "&8{map}");
        }
    }

    public void start() {
        stopTaskOnly();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 0L, 20L);
    }

    public void stop() {
        stopTaskOnly();
        for (UUID uuid : List.copyOf(previousScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restore(player);
            }
        }
        previousScoreboards.clear();
        activeScoreboards.clear();
        renderedEntries.clear();
        tabScoreboardDisabled.clear();
    }

    public void restore(Player player) {
        restoreTabScoreboard(player);
        activeScoreboards.remove(player.getUniqueId());
        renderedEntries.remove(player.getUniqueId());
        Scoreboard previous = previousScoreboards.remove(player.getUniqueId());
        if (previous != null) {
            player.setScoreboard(previous);
        }
    }

    public void capture(Player player) {
        previousScoreboards.putIfAbsent(player.getUniqueId(), player.getScoreboard());
        disableTabScoreboard(player);
        apply(player);
    }

    private void update() {
        EventSession session = eventManager.session();
        if (session == null || session.phase() == EventPhase.VOTE) {
            return;
        }
        for (UUID uuid : eventManager.eventPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                capture(player);
            }
        }
    }

    private void apply(Player player) {
        EventSession session = eventManager.session();
        if (session == null) {
            restore(player);
            return;
        }
        Scoreboard scoreboard = activeScoreboards.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = scoreboard.getObjective("eevent");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("eevent", "dummy", color(title));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.setDisplayName(color(title));
        }
        List<String> lines = lines(session, player);
        List<String> previousEntries = renderedEntries.getOrDefault(player.getUniqueId(), List.of());
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String entry = uniqueEntry(index);
            entries.add(entry);
            Team team = scoreboard.getTeam("ee_line_" + index);
            if (team == null) {
                team = scoreboard.registerNewTeam("ee_line_" + index);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.setPrefix(lines.get(index));
            objective.getScore(entry).setScore(lines.size() - index);
        }
        for (String previous : previousEntries) {
            if (!entries.contains(previous)) {
                scoreboard.resetScores(previous);
                Team team = scoreboard.getEntryTeam(previous);
                if (team != null) {
                    team.removeEntry(previous);
                }
            }
        }
        renderedEntries.put(player.getUniqueId(), entries);
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    private List<String> lines(EventSession session, Player player) {
        List<String> template = templateFor(session.definition().type());
        List<String> lines = new ArrayList<>();
        long teamCount = session.teams().values().stream().distinct().count();
        String map = session.selectedMap() == null ? "" : session.selectedMap().id();
        EventType type = session.definition().type();
        boolean teamEvent = type == EventType.CAPTURE_THE_FLAG || type == EventType.CAPTURE_PLAYERS || type == EventType.FIGHT_2V2
                || type == EventType.SUMO_2V2 || type == EventType.BEDWARS;
        String team = eventManager.teamFor(player.getUniqueId());
        String teamDisplay = team.isBlank() ? "-" : displayTeam(team);
        String remaining = formatDuration(eventManager.activeSecondsRemaining());
        String blockPartyRound = eventManager.runtimeScoreboardValue("block-party-round", "0");
        String redScore = eventManager.runtimeScoreboardValue("ctf-score-red", eventManager.runtimeScoreboardValue("ctf-score-1", "0"));
        String blueScore = eventManager.runtimeScoreboardValue("ctf-score-blue", eventManager.runtimeScoreboardValue("ctf-score-2", "0"));
        String quakeScore = eventManager.runtimeScoreboardValue("quake-score-" + player.getUniqueId(), "0");
        int blank = 0;
        for (String configuredLine : template) {
            String line = configuredLine
                    .replace("{event}", session.definition().displayName())
                    .replace("{phase}", session.phase().name())
                    .replace("{players}", String.valueOf(session.participants().size()))
                    .replace("{spectators}", String.valueOf(session.spectators().size()))
                    .replace("{teams}", teamEvent ? String.valueOf(teamCount) : "-")
                    .replace("{finished}", String.valueOf(session.finalRankings().size()))
                    .replace("{eliminated}", String.valueOf(session.spectators().size()))
                    .replace("{team}", teamDisplay)
                    .replace("{timeleft}", remaining)
                    .replace("{round}", blockPartyRound)
                    .replace("{redscore}", redScore)
                    .replace("{bluescore}", blueScore)
                    .replace("{score}", quakeScore)
                    .replace("{checkpoint}", eventManager.runtimeScoreboardValue("checkpoint-" + player.getUniqueId(), "-"))
                    .replace("{map}", map);
            if (line.isBlank()) {
                lines.add(uniqueBlank(blank++));
            } else {
                lines.add(color(line));
            }
        }
        return lines.stream().limit(14).toList();
    }

    private List<String> templateFor(EventType type) {
        return switch (type) {
            case CAPTURE_THE_FLAG -> List.of(
                    "&8&m---------------",
                    "&c&lCapture the Flag",
                    "&7Team: &f{team}",
                    "&7Time left: &e{timeleft}",
                    "",
                    "&cRed: &f{redscore}&7/3",
                    "&9Blue: &f{bluescore}&7/3",
                    "",
                    "&7Players: &f{players}",
                    "&8{map}"
            );
            case BLOCK_PARTY -> List.of(
                    "&8&m---------------",
                    "&d&lBlock Party",
                    "&7Time left: &e{timeleft}",
                    "",
                    "&7Round: &f{round}",
                    "&7Alive: &a{players}",
                    "&7Eliminated: &c{eliminated}",
                    "",
                    "&8{map}"
            );
            case BOAT_RACE, HORSE_RACE, PARKOUR, ELYTRA_RACE -> List.of(
                    "&8&m---------------",
                    "&b&l{event}",
                    "&7Time left: &e{timeleft}",
                    "",
                    "&7Racers: &f{players}",
                    "&7Finished: &a{finished}",
                    "&7Checkpoint: &f{checkpoint}",
                    "",
                    "&8{map}"
            );
            case QUAKE -> List.of(
                    "&8&m---------------",
                    "&e&lQuake",
                    "&7Time left: &e{timeleft}",
                    "",
                    "&7Score: &f{score}",
                    "&7Players: &f{players}",
                    "",
                    "&8{map}"
            );
            case FIGHT_2V2, SUMO_2V2, BEDWARS, CAPTURE_PLAYERS -> List.of(
                    "&8&m---------------",
                    "&6&l{event}",
                    "&7Team: &f{team}",
                    "&7Time left: &e{timeleft}",
                    "",
                    "&7Players: &f{players}",
                    "&7Spectators: &f{spectators}",
                    "",
                    "&8{map}"
            );
            default -> configuredLines;
        };
    }

    private String displayTeam(String team) {
        return switch (team == null ? "" : team.toLowerCase(java.util.Locale.ROOT)) {
            case "1", "red" -> ChatColor.RED + "Red";
            case "2", "blue" -> ChatColor.BLUE + "Blue";
            case "3", "green" -> ChatColor.GREEN + "Green";
            case "4", "yellow" -> ChatColor.YELLOW + "Yellow";
            default -> "Team " + team;
        };
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0L) {
            return "--";
        }
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        if (minutes <= 0L) {
            return remainder + "s";
        }
        return minutes + "m " + String.format(java.util.Locale.ROOT, "%02d", remainder) + "s";
    }

    private String uniqueBlank(int index) {
        return ChatColor.values()[index].toString();
    }

    private String uniqueEntry(int index) {
        ChatColor color = ChatColor.values()[index % ChatColor.values().length];
        return color.toString() + ChatColor.RESET;
    }

    private void stopTaskOnly() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void disableTabScoreboard(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB") || tabScoreboardDisabled.contains(player.getUniqueId())) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard off " + player.getName() + " -s");
        tabScoreboardDisabled.add(player.getUniqueId());
    }

    private void restoreTabScoreboard(Player player) {
        if (!tabScoreboardDisabled.remove(player.getUniqueId()) || !Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard on " + player.getName() + " -s");
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private void writeDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("config-version", 1);
        yaml.set("active.title", title);
        yaml.set("active.lines", configuredLines);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create scoreboards/scoreboards.yml: " + e.getMessage());
        }
    }
}
