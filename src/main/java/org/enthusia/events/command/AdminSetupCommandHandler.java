package org.enthusia.events.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.setup.SetupSession;
import org.enthusia.events.setup.SetupTool;
import org.enthusia.events.setup.SetupWizard;

import java.util.Locale;
import java.util.Map;

final class AdminSetupCommandHandler {

    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService mapSetupService;
    private final SetupWizard setupWizard;

    AdminSetupCommandHandler(EnthusiaEventsPlugin plugin, MapSetupService mapSetupService, SetupWizard setupWizard) {
        this.plugin = plugin;
        this.mapSetupService = mapSetupService;
        this.setupWizard = setupWizard;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            return cancel(player);
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("save")) {
            return save(player);
        }
        if (args.length < 3) {
            sender.sendMessage("/ee setup <EVENT> <mapId> <pos1|pos2|spawn|spectator|checkpoint|checkpoint_spawn|finish|chest|generator|point|area_pos1|area_pos2|area> [name|tier|type]");
            return true;
        }
        EventType eventType = AdminCommandSupport.parseEvent(plugin, sender, args[1]);
        if (eventType == null) {
            return true;
        }
        String mapId = args[2];
        if (mapSetupService.find(eventType, mapId).isEmpty()) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "map not found; run /ee map create first"));
            return true;
        }
        if (args.length == 3) {
            setupWizard.openPalette(player, eventType, mapId);
            return true;
        }
        if (args[3].equalsIgnoreCase("save")) {
            return saveSelectedMap(player, eventType, mapId);
        }
        SetupTool tool = parseTool(sender, args[3]);
        if (tool == null) {
            return true;
        }
        String value = args.length >= 5 ? args[4] : "";
        if (requiresValue(tool) && value.isBlank()) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "that setup mode needs a name, tier, or type"));
            return true;
        }
        if (tool == SetupTool.CHEST && !isValidChestTier(sender, value)) {
            return true;
        }
        setupWizard.begin(player, new SetupSession(eventType, mapId, tool, value));
        return true;
    }

    private boolean cancel(Player player) {
        if (setupWizard.cancel(player)) {
            plugin.messages().send(player, "setup-cancelled");
        } else {
            plugin.messages().send(player, "setup-failed", Map.of("reason", "no setup mode was active"));
        }
        return true;
    }

    private boolean saveSelectedMap(Player player, EventType eventType, String mapId) {
        SetupSession active = setupWizard.session(player).orElse(null);
        if (active == null || active.eventType() != eventType || !active.mapId().equalsIgnoreCase(mapId)) {
            plugin.messages().send(player, "setup-failed", Map.of(
                    "reason", "open that map first with /ee setup " + eventType.name() + " " + mapId
            ));
            return true;
        }
        return save(player);
    }

    private boolean save(Player player) {
        if (setupWizard.session(player).isEmpty()) {
            plugin.messages().send(player, "setup-failed", Map.of("reason", "no setup mode was active"));
            return true;
        }
        if (setupWizard.saveAndClose(player)) {
            plugin.messages().send(player, "setup-saved-finished");
        }
        return true;
    }

    private SetupTool parseTool(CommandSender sender, String rawTool) {
        try {
            return SetupTool.valueOf(rawTool.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "unknown setup mode"));
            return null;
        }
    }

    private boolean requiresValue(SetupTool tool) {
        return tool == SetupTool.SPAWN
                || tool == SetupTool.CHECKPOINT
                || tool == SetupTool.CHECKPOINT_SPAWN
                || tool == SetupTool.CHEST
                || tool == SetupTool.GENERATOR
                || tool == SetupTool.POINT
                || tool == SetupTool.AREA;
    }

    private boolean isValidChestTier(CommandSender sender, String value) {
        try {
            int tier = Integer.parseInt(value);
            if (tier >= 1 && tier <= 3) {
                return true;
            }
            plugin.messages().send(sender, "setup-failed", Map.of("reason", "chest tier must be 1, 2, or 3"));
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "invalid-number", Map.of("value", value));
        }
        return false;
    }
}
