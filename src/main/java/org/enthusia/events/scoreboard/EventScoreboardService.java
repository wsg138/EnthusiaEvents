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

@SuppressWarnings({
        "PMD.UseConcurrentHashMap",
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.NullAssignment"
})
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
        if (session.definition().type() == EventType.BEDWARS) {
            return bedWarsLines(session, player);
        }
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
        String oneInTheChamberScore = eventManager.runtimeScoreboardValue("oitc-score-" + player.getUniqueId(), "0");
        String captureRound = eventManager.runtimeScoreboardValue("capture-players-round", "1");
        String captureRedWins = eventManager.runtimeScoreboardValue("capture-players-round-win-red",
                eventManager.runtimeScoreboardValue("capture-players-round-win-1", "0"));
        String captureBlueWins = eventManager.runtimeScoreboardValue("capture-players-round-win-blue",
                eventManager.runtimeScoreboardValue("capture-players-round-win-2", "0"));
        String captureRedJailed = eventManager.runtimeScoreboardValue("capture-players-jailed-red",
                eventManager.runtimeScoreboardValue("capture-players-jailed-1", "0"));
        String captureBlueJailed = eventManager.runtimeScoreboardValue("capture-players-jailed-blue",
                eventManager.runtimeScoreboardValue("capture-players-jailed-2", "0"));
        int blank = 0;
        String scorePrefix = type == EventType.ONE_IN_THE_CHAMBER ? "oitc-score-" : "quake-score-";
        String[] top5 = topScores(session, scorePrefix);
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
                    .replace("{oitc_score}", oneInTheChamberScore)
                    .replace("{capture_round}", captureRound)
                    .replace("{capture_red_wins}", captureRedWins)
                    .replace("{capture_blue_wins}", captureBlueWins)
                    .replace("{capture_red_jailed}", captureRedJailed)
                    .replace("{capture_blue_jailed}", captureBlueJailed)
                    .replace("{top1}", top5[0])
                    .replace("{top2}", top5[1])
                    .replace("{top3}", top5[2])
                    .replace("{top4}", top5[3])
                    .replace("{top5}", top5[4])
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

    private String[] topScores(EventSession session, String scorePrefix) {
        List<Map.Entry<UUID, String>> sorted = new ArrayList<>();
        for (UUID uuid : session.participants()) {
            String score = eventManager.runtimeScoreboardValue(scorePrefix + uuid, "0");
            if (!"0".equals(score)) {
                sorted.add(Map.entry(uuid, score));
            }
        }
        sorted.sort((a, b) -> Integer.compare(Integer.parseInt(b.getValue()), Integer.parseInt(a.getValue())));
        String[] top5 = {"-", "-", "-", "-", "-"};
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(sorted.get(i).getKey());
            String name = player.getName() == null ? "?" : player.getName();
            top5[i] = name + ": " + sorted.get(i).getValue();
        }
        return top5;
    }

    private List<String> bedWarsLines(EventSession session, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(color("&8&m---------------"));
        lines.add(color("&c&lBedWars"));
        lines.add(color("&7Team: &f" + displayTeam(eventManager.teamFor(player.getUniqueId()))));
        lines.add(color("&7Time left: &e" + formatDuration(eventManager.activeSecondsRemaining())));
        lines.add(uniqueBlank(0));
        lines.add(color("&7Team     Bed   Players"));
        List<String> teams = session.teams().values().stream().distinct().sorted().toList();
        for (String team : teams) {
            boolean bedAlive = Boolean.parseBoolean(eventManager.runtimeScoreboardValue(
                    "bedwars-bed-" + team.toLowerCase(java.util.Locale.ROOT), "true"
            ));
            long playersAlive = session.teams().entrySet().stream()
                    .filter(entry -> team.equals(entry.getValue()) && session.participants().contains(entry.getKey()))
                    .count();
            String teamMarker = teamColorCode(team) + "\u25A0";
            String bedMarker = bedAlive ? "&a\u2714" : "&c\u2716";
            String playerMarker = playersAlive > 0 ? "&f" + playersAlive : "&8\u2716";
            // Center markers under "Team     Bed   Players" header
            lines.add(color(" " + teamMarker + "        " + bedMarker + "      " + playerMarker));
        }
        lines.add(uniqueBlank(1));
        lines.add(color("&8" + (session.selectedMap() == null ? "" : session.selectedMap().id())));
        return lines.stream().limit(14).toList();
    }

    private String teamColorCode(String team) {
        return switch (team == null ? "" : team.toLowerCase(java.util.Locale.ROOT)) {
            case "1", "red" -> "&c";
            case "2", "blue" -> "&9";
            case "3", "green" -> "&a";
            case "4", "yellow" -> "&e";
            case "5", "orange" -> "&6";
            case "6", "purple" -> "&5";
            case "7", "cyan" -> "&b";
            default -> "&f";
        };
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
                    "&7Top Players:",
                    "&f{top1}",
                    "&f{top2}",
                    "&f{top3}",
                    "&f{top4}",
                    "&f{top5}",
                    "",
                    "&7Players: &f{players}",
                    "&8{map}"
            );
            case ONE_IN_THE_CHAMBER -> List.of(
                    "&8&m---------------",
                    "&6&lOne in the Chamber",
                    "&7Time left: &e{timeleft}",
                    "&7Your Kills: &f{oitc_score}",
                    "",
                    "&7Top Players:",
                    "&f{top1}",
                    "&f{top2}",
                    "&f{top3}",
                    "&f{top4}",
                    "&f{top5}",
                    "",
                    "&7Players: &f{players}",
                    "&8{map}"
            );
            case FIGHT_2V2, SUMO_2V2 -> List.of(
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
            case CAPTURE_PLAYERS -> List.of(
                    "&8&m---------------",
                    "&6&lCapture Players",
                    "&7Team: &f{team}",
                    "&7Time left: &e{timeleft}",
                    "",
                    "&eRound &f{capture_round}",
                    "&7Round Wins",
                    "&cRed: &f{capture_red_wins}  &9Blue: &f{capture_blue_wins}",
                    "",
                    "&7Players Jailed",
                    "&4Red: &f{capture_red_jailed}  &1Blue: &f{capture_blue_jailed}",
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
