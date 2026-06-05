package org.enthusia.events.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.setup.SetupWizard;
import org.enthusia.events.util.LocationCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class SetupCommand implements CommandExecutor, TabCompleter {

    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService mapSetupService;
    private final SetupWizard setupWizard;

    public SetupCommand(EnthusiaEventsPlugin plugin, MapSetupService mapSetupService, SetupWizard setupWizard) {
        this.plugin = plugin;
        this.mapSetupService = mapSetupService;
        this.setupWizard = setupWizard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!sender.hasPermission("enthusia.events.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("save")) {
            if (setupWizard.session(player).isEmpty()) {
                plugin.messages().send(player, "setup-failed", java.util.Map.of("reason", "no setup mode was active"));
                return true;
            }
            if (setupWizard.saveAndClose(player)) {
                plugin.messages().send(player, "setup-saved-finished");
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
            if (setupWizard.cancel(player)) {
                plugin.messages().send(player, "setup-cancelled");
            } else {
                plugin.messages().send(player, "setup-failed", java.util.Map.of("reason", "no setup mode was active"));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("hub")) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("pos1") || args[1].equalsIgnoreCase("pos2"))) {
                setRegionPoint(player, "locations.waiting-hub-region." + args[1].toLowerCase(Locale.ROOT), "waiting hub region " + args[1].toLowerCase(Locale.ROOT));
                return true;
            }
            plugin.getConfig().set("locations.waiting-hub", LocationCodec.encode(player.getLocation()));
            plugin.saveConfig();
            plugin.messages().send(player, "setup-saved", java.util.Map.of("target", "waiting hub"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("trophy")) {
            if (args.length >= 2 && (args[1].equalsIgnoreCase("pos1") || args[1].equalsIgnoreCase("pos2"))) {
                setRegionPoint(player, "locations.trophy-room-region." + args[1].toLowerCase(Locale.ROOT), "trophy room region " + args[1].toLowerCase(Locale.ROOT));
                return true;
            }
            plugin.getConfig().set("locations.trophy-room", LocationCodec.encode(player.getLocation()));
            plugin.saveConfig();
            plugin.messages().send(player, "setup-saved", java.util.Map.of("target", "trophy room"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("/setup <event> [mapId]");
            return true;
        }
        EventType type;
        try {
            type = EventType.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "invalid-event", java.util.Map.of("event", args[0]));
            return true;
        }
        String mapId = args.length >= 2 ? args[1] : type.name().toLowerCase(Locale.ROOT);
        mapSetupService.create(type, mapId, player.getWorld().getName());
        setupWizard.openPalette(player, type, mapId);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("cancel");
            values.add("save");
            values.add("hub");
            values.add("trophy");
            for (EventType type : EventType.values()) {
                values.add(type.name());
            }
            return filter(args[0], values);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("hub") || args[0].equalsIgnoreCase("trophy")) {
                return filter(args[1], List.of("pos1", "pos2"));
            }
            EventType type;
            try {
                type = EventType.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return List.of();
            }
            return filter(args[1], mapSetupService.mapsFor(type).stream().map(map -> map.id()).toList());
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private void setRegionPoint(Player player, String path, String target) {
        plugin.getConfig().set(path, LocationCodec.encode(player.getLocation()));
        plugin.saveConfig();
        plugin.messages().send(player, "setup-saved", Map.of("target", target));
    }
}
