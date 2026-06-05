package org.enthusia.events.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventDefinition;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;
import org.enthusia.events.gui.EventVoteGui;
import org.enthusia.events.stats.EventStatsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
public final class EventCommand implements CommandExecutor, TabCompleter {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final EventStatsService statsService;
    private final EventVoteGui voteGui;

    public EventCommand(EnthusiaEventsPlugin plugin, EventManager eventManager, EventStatsService statsService, EventVoteGui voteGui) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.statsService = statsService;
        this.voteGui = voteGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            EventSession session = eventManager.session();
            if (session == null) {
                plugin.messages().send(player, "event-not-running");
            } else {
                plugin.messages().send(player, "active-event", Map.of(
                        "phase", session.phase().name(),
                        "event", session.definition().displayName()
                ));
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> {
                if (!eventManager.join(player)) {
                    EventSession session = eventManager.session();
                    if (session == null) {
                        plugin.messages().send(player, "event-not-running");
                    } else {
                        plugin.messages().send(player, "event-join-closed", Map.of("phase", session.phase().name()));
                    }
                }
            }
            case "spectate" -> {
                if (!eventManager.spectate(player)) {
                    plugin.messages().send(player, "event-spectate-failed");
                }
            }
            case "vote" -> voteGui.open(player);
            case "leave" -> {
                if (!eventManager.leave(player)) {
                    plugin.messages().send(player, "event-not-running");
                }
            }
            case "start" -> {
                if (eventManager.hasSession()) {
                    plugin.messages().send(player, "event-start-failed", Map.of(
                            "reason", eventManager.manualStartFailureReason(player, null, false)
                    ));
                    return true;
                }
                EventDefinition selected = null;
                if (args.length < 2) {
                    voteGui.openStart(player);
                    return true;
                } else {
                    try {
                        selected = plugin.eventRegistry().definition(EventType.valueOf(args[1].toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                    if (selected == null) {
                        plugin.messages().send(player, "invalid-event", Map.of("event", args[1]));
                        return true;
                    }
                }
                if (!eventManager.startManualVote(player, selected, false)) {
                    plugin.messages().send(player, "event-start-failed", Map.of(
                            "reason", eventManager.manualStartFailureReason(player, selected, false)
                    ));
                }
            }
            case "stats" -> statsService.sendSummary(player);
            case "next", "time", "timer" ->
                    plugin.messages().send(player, "next-event", Map.of("time", eventManager.nextHourlyVoteLabel()));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("join", "leave", "spectate", "vote", "start", "stats", "next", "time", "timer"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filter(args[1], List.of(EventType.values()).stream().map(Enum::name).toList());
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
