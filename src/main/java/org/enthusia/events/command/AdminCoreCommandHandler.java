package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.gui.RestoreConfirmGui;
import org.enthusia.events.util.TeleportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class AdminCoreCommandHandler {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final MapSetupService mapSetupService;
    private final Map<String, AdminCommandAction> actions;

    AdminCoreCommandHandler(EnthusiaEventsPlugin plugin, EventManager eventManager,
                            MapSetupService mapSetupService, RestoreConfirmGui restoreConfirmGui) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.mapSetupService = mapSetupService;
        AdminMaintenanceCommandHandler maintenance = new AdminMaintenanceCommandHandler(
                plugin, eventManager, restoreConfirmGui
        );
        this.actions = Map.ofEntries(
                Map.entry("autostart", this::handleAutoStart),
                Map.entry("disable", this::disableEvent),
                Map.entry("enable", this::enableEvent),
                Map.entry("enabled", this::sendEnabledEvents),
                Map.entry("disabled", this::sendDisabledEvents),
                Map.entry("status", this::sendStatus),
                Map.entry("private", this::handlePrivateEvent),
                Map.entry("invite", this::handleInvite),
                Map.entry("forcestart", this::handleForceStart),
                Map.entry("simulatevote", this::handleForceStart),
                Map.entry("advance", this::handleAdvance),
                Map.entry("forcestop", this::handleForceStop),
                Map.entry("eventtp", this::handleEventTeleport),
                Map.entry("eventteleport", this::handleEventTeleport),
                Map.entry("stop", this::handleStop),
                Map.entry("restore", maintenance::restore),
                Map.entry("stuckcheck", maintenance::stuckCheck),
                Map.entry("emergencyrestore", maintenance::emergencyRestore),
                Map.entry("remove", maintenance::remove),
                Map.entry("reload", maintenance::reload),
                Map.entry("retryrestores", maintenance::retryRestores),
                Map.entry("resetconfigs", maintenance::resetConfigs),
                Map.entry("resetloot", maintenance::resetLoot)
        );
    }

    boolean handle(CommandSender sender, String[] args) {
        AdminCommandAction action = actions.get(args[0].toLowerCase(Locale.ROOT));
        return action != null && action.execute(sender, args);
    }

    private boolean handleAutoStart(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            plugin.messages().send(sender, "event-autostart-status", Map.of(
                    "state", plugin.getConfig().getBoolean("schedule.enabled", false) ? "enabled" : "disabled"
            ));
            return true;
        }
        Boolean enabled = parseEnabledState(args[1]);
        if (enabled == null) {
            sender.sendMessage("/ee autostart <on|off|status>");
            return true;
        }
        plugin.getConfig().set("schedule.enabled", enabled);
        plugin.saveConfig();
        plugin.messages().send(sender, "event-autostart-updated", Map.of("state", enabled ? "enabled" : "disabled"));
        return true;
    }

    private Boolean parseEnabledState(String value) {
        if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("enable") || value.equalsIgnoreCase("enabled")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("disable") || value.equalsIgnoreCase("disabled")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean disableEvent(CommandSender sender, String[] args) {
        return handleEventToggle(sender, args, true);
    }

    private boolean enableEvent(CommandSender sender, String[] args) {
        return handleEventToggle(sender, args, false);
    }

    private boolean handleEventToggle(CommandSender sender, String[] args, boolean disabled) {
        if (args.length < 2) {
            sender.sendMessage(disabled ? "/ee disable <EVENT>" : "/ee enable <EVENT>");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[1]);
        if (type == null) {
            return true;
        }
        eventManager.setEventDisabled(type, disabled);
        plugin.messages().send(sender, disabled ? "event-disabled" : "event-enabled", Map.of("event", type.name()));
        return true;
    }

    private boolean sendEnabledEvents(CommandSender sender, String[] args) {
        List<String> enabled = eventManager.enabledEvents().stream().map(Enum::name).toList();
        plugin.messages().send(sender, "event-enabled-list", Map.of(
                "events", enabled.isEmpty() ? "none" : String.join(", ", enabled)
        ));
        return true;
    }

    private boolean sendDisabledEvents(CommandSender sender, String[] args) {
        List<String> disabled = eventManager.disabledEvents().stream().map(Enum::name).toList();
        plugin.messages().send(sender, "event-disabled-list", Map.of(
                "events", disabled.isEmpty() ? "none" : String.join(", ", disabled)
        ));
        return true;
    }

    private boolean sendStatus(CommandSender sender, String[] args) {
        plugin.messages().send(sender, "event-admin-status", Map.of("status", eventManager.adminStatusLine()));
        return true;
    }

    private boolean handlePrivateEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/ee private <EVENT> [player...]");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[1]);
        if (type == null) {
            return true;
        }
        List<Player> invited = resolveInvitedPlayers(sender, args);
        if (invited == null) {
            return true;
        }
        if (!eventManager.startPrivateEvent(sender, type, invited)) {
            plugin.messages().send(sender, "force-start-failed", Map.of("reason", eventManager.privateStartFailureReason(type)));
            return true;
        }
        plugin.messages().send(sender, "private-event-started", Map.of(
                "event", type.name(),
                "players", invited.isEmpty() ? "none yet" : invited.stream()
                        .map(Player::getName)
                        .collect(Collectors.joining(", "))
        ));
        return true;
    }

    private List<Player> resolveInvitedPlayers(CommandSender sender, String[] args) {
        List<Player> invited = new ArrayList<>();
        if (sender instanceof Player player) {
            invited.add(player);
        }
        for (int index = 2; index < args.length; index++) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", Map.of("player", args[index]));
                return null;
            }
            if (!invited.contains(target)) {
                invited.add(target);
            }
        }
        return invited;
    }

    private boolean handleInvite(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/ee invite <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }
        if (!eventManager.invitePrivatePlayer(target)) {
            plugin.messages().send(sender, "private-event-invite-failed");
            return true;
        }
        plugin.messages().send(sender, "private-event-player-invited", Map.of("player", target.getName()));
        return true;
    }

    private boolean handleForceStart(CommandSender sender, String[] args) {
        boolean simulateVote = args[0].equalsIgnoreCase("simulatevote");
        boolean started;
        EventType requestedType = null;
        if (simulateVote) {
            started = eventManager.startScheduledVote();
        } else if (args.length == 1 && eventManager.isPrivateSessionWaiting()) {
            started = eventManager.advancePhase();
        } else if (args.length >= 2) {
            requestedType = AdminCommandSupport.parseEvent(plugin, sender, args[1]);
            if (requestedType == null) {
                return true;
            }
            started = eventManager.startForcedEvent(requestedType);
        } else {
            started = eventManager.startScheduledVote();
            if (started) {
                plugin.messages().send(sender, "force-started", Map.of("event", "random vote"));
            }
        }
        if (!started) {
            String reason = requestedType == null
                    ? eventManager.manualStartFailureReason(sender instanceof Player player ? player : null, null, false)
                    : eventManager.forcedStartFailureReason(requestedType);
            plugin.messages().send(sender, "force-start-failed", Map.of("reason", reason));
        }
        return true;
    }

    private boolean handleAdvance(CommandSender sender, String[] args) {
        if (eventManager.advancePhase()) {
            String phase = eventManager.session() == null ? "ended" : eventManager.session().phase().name();
            plugin.messages().send(sender, "event-advanced", Map.of("phase", phase));
        } else {
            plugin.messages().send(sender, "event-advance-failed");
        }
        return true;
    }

    private boolean handleForceStop(CommandSender sender, String[] args) {
        eventManager.stop("admin-force");
        plugin.messages().send(sender, "force-stopped");
        return true;
    }

    private boolean handleEventTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("/ee eventtp <EVENT> [mapId]");
            return true;
        }
        EventType type = AdminCommandSupport.parseEvent(plugin, sender, args[1]);
        if (type == null) {
            return true;
        }
        Optional<EventMap> map = args.length >= 3
                ? mapSetupService.find(type, args[2])
                : mapSetupService.mapsFor(type).stream().findFirst();
        if (map.isEmpty()) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found"));
            return true;
        }
        Location target = AdminCommandSupport.mapTeleportLocation(map.get());
        if (target == null) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map has no loaded world or usable location"));
            return true;
        }
        TeleportService.teleport(plugin, player, target, "admin event map teleport");
        plugin.messages().send(sender, "setup-saved", Map.of("target", "teleport to " + type.name() + " map " + map.get().id()));
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        eventManager.stop("admin");
        plugin.messages().send(sender, "admin-stop");
        return true;
    }
}
