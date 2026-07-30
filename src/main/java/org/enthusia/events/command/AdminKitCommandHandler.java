package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.kit.EventKitService;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class AdminKitCommandHandler {

    private final EnthusiaEventsPlugin plugin;
    private final EventKitService kitService;

    AdminKitCommandHandler(EnthusiaEventsPlugin plugin, EventKitService kitService) {
        this.plugin = plugin;
        this.kitService = kitService;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/ee kit <save|list|select|override>");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "save" -> save(sender, args);
            case "list" -> list(sender);
            case "select" -> select(sender, args);
            case "override" -> override(sender, args);
            default -> false;
        };
    }

    private boolean save(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("/ee kit save <name>");
            return true;
        }
        if (kitService.saveKit(player, args[2])) {
            plugin.messages().send(sender, "kit-saved", Map.of("kit", args[2]));
        } else {
            plugin.messages().send(sender, "setup-failed", Map.of(
                    "reason", "kit name can only contain letters, numbers, underscore, or dash"
            ));
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        String kits = kitService.kits().stream()
                .map(EventKitService.EventKit::name)
                .collect(Collectors.joining(", "));
        plugin.messages().send(sender, "kit-list", Map.of("kits", kits.isBlank() ? "none" : kits));
        return true;
    }

    private boolean select(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("/ee kit select <name>");
            return true;
        }
        if (kitService.selectKit(player, args[2])) {
            plugin.messages().send(sender, "kit-selected", Map.of("kit", args[2]));
        } else {
            plugin.messages().send(sender, "kit-not-found", Map.of("kit", args[2]));
        }
        return true;
    }

    private boolean override(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("/ee kit override <player> <kit>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "event-no-snapshot");
            return true;
        }
        if (kitService.selectKit(target, args[3])) {
            plugin.messages().send(sender, "kit-override", Map.of("player", target.getName(), "kit", args[3]));
        } else {
            plugin.messages().send(sender, "kit-not-found", Map.of("kit", args[3]));
        }
        return true;
    }
}
