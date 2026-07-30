package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.gui.RestoreConfirmGui;

import java.util.Map;

final class AdminMaintenanceCommandHandler {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final RestoreConfirmGui restoreConfirmGui;

    AdminMaintenanceCommandHandler(EnthusiaEventsPlugin plugin, EventManager eventManager,
                                   RestoreConfirmGui restoreConfirmGui) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.restoreConfirmGui = restoreConfirmGui;
    }

    boolean restore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "event-no-snapshot");
            return true;
        }
        if (sender instanceof Player admin) {
            restoreConfirmGui.open(admin, target);
            return true;
        }
        plugin.messages().send(sender, "event-restore-started", Map.of("player", target.getName()));
        eventManager.restoreSnapshot(target).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () ->
                plugin.messages().send(sender, restored ? "event-restored" : "event-restore-failed-staff",
                        Map.of("player", target.getName()))));
        return true;
    }

    boolean stuckCheck(CommandSender sender, String[] args) {
        Player target = requireOnlineTarget(sender, args, "/ee stuckcheck <player>");
        if (target != null) {
            sender.sendMessage(eventManager.stuckCheck(target));
        }
        return true;
    }

    boolean emergencyRestore(CommandSender sender, String[] args) {
        Player target = requireOnlineTarget(sender, args, "/ee emergencyrestore <player>");
        if (target == null) {
            return true;
        }
        plugin.messages().send(sender, "event-emergency-restore-started", Map.of("player", target.getName()));
        eventManager.emergencyRestore(target).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () ->
                plugin.messages().send(sender,
                        restored ? "event-emergency-restore-done" : "event-emergency-restore-partial",
                        Map.of("player", target.getName()))));
        return true;
    }

    boolean remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target != null) {
            eventManager.leave(target);
        }
        return true;
    }

    boolean reload(CommandSender sender, String[] args) {
        plugin.messages().send(sender, plugin.reloadPlugin() ? "reload-done" : "reload-blocked-active-event");
        return true;
    }

    boolean retryRestores(CommandSender sender, String[] args) {
        int count = eventManager.retryPendingOnlineRestores();
        plugin.messages().send(sender, "event-restore-retry-started", Map.of("count", String.valueOf(count)));
        return true;
    }

    boolean resetConfigs(CommandSender sender, String[] args) {
        plugin.resetGeneratedConfigs();
        plugin.messages().send(sender, "config-reset-done");
        return true;
    }

    boolean resetLoot(CommandSender sender, String[] args) {
        plugin.resetLootConfig();
        plugin.messages().send(sender, "loot-reset-done");
        return true;
    }

    private Player requireOnlineTarget(CommandSender sender, String[] args, String usage) {
        if (args.length < 2) {
            sender.sendMessage(usage);
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
        }
        return target;
    }
}
