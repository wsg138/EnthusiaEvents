from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


plugin = "src/main/java/org/enthusia/events/EnthusiaEventsPlugin.java"
replace_once(
    plugin,
    "import org.enthusia.events.event.EventRegistry;\nimport org.enthusia.events.event.EventScheduler;",
    "import org.enthusia.events.event.EventRegistry;\nimport org.enthusia.events.event.EventSessionHardeningListener;\nimport org.enthusia.events.event.EventScheduler;",
)
replace_once(
    plugin,
    """        gameplayListener = new EventGameplayListener(this, eventManager);\n        EventGameplaySafetyListener gameplaySafetyListener = new EventGameplaySafetyListener(this, eventManager);\n        eventManager.gameplayRuntimeReset(gameplayListener::resetForNewSession);\n        eventManager.gameplayPreStart(gameplayListener::prepareSession);""",
    """        gameplayListener = new EventGameplayListener(this, eventManager);\n        EventGameplaySafetyListener gameplaySafetyListener = new EventGameplaySafetyListener(this, eventManager);\n        EventSessionHardeningListener sessionHardeningListener = new EventSessionHardeningListener(this, eventManager);\n        eventManager.gameplayRuntimeReset(gameplayListener::resetForNewSession);\n        eventManager.gameplayPreStart(session -> {\n            gameplayListener.prepareSession(session);\n            sessionHardeningListener.prepareSession(session);\n        });""",
)
replace_once(
    plugin,
    """        Bukkit.getPluginManager().registerEvents(new EventDurabilityListener(eventManager), this);\n        Bukkit.getPluginManager().registerEvents(gameplaySafetyListener, this);\n        Bukkit.getPluginManager().registerEvents(gameplayListener, this);""",
    """        Bukkit.getPluginManager().registerEvents(new EventDurabilityListener(eventManager), this);\n        Bukkit.getPluginManager().registerEvents(gameplaySafetyListener, this);\n        Bukkit.getPluginManager().registerEvents(sessionHardeningListener, this);\n        Bukkit.getPluginManager().registerEvents(gameplayListener, this);""",
)
replace_once(
    plugin,
    """        Bukkit.getScheduler().runTaskTimer(this, bedWarsPolishListener::tickWinCondition, 1L, 1L);\n        Bukkit.getScheduler().runTaskTimer(this, gameplaySafetyListener::tickCtfArmor, 1L, 5L);""",
    """        Bukkit.getScheduler().runTaskTimer(this, bedWarsPolishListener::tickWinCondition, 1L, 1L);\n        Bukkit.getScheduler().runTaskTimer(this, gameplaySafetyListener::tickCtfArmor, 1L, 5L);\n        Bukkit.getScheduler().runTaskTimer(this, sessionHardeningListener::tick, 1L, 20L);""",
)

kit = "src/main/java/org/enthusia/events/kit/KitVoteListener.java"
replace_once(
    kit,
    """import org.bukkit.event.inventory.InventoryClickEvent;\nimport org.bukkit.event.player.PlayerInteractEvent;""",
    """import org.bukkit.event.inventory.InventoryClickEvent;\nimport org.bukkit.event.inventory.InventoryDragEvent;\nimport org.bukkit.event.player.PlayerDropItemEvent;\nimport org.bukkit.event.player.PlayerInteractEvent;\nimport org.bukkit.event.player.PlayerSwapHandItemsEvent;""",
)
replace_once(
    kit,
    """    @EventHandler\n    public void onInventoryClick(InventoryClickEvent event) {\n        if (event.getInventory().getHolder() instanceof KitPreviewHolder) {\n            event.setCancelled(true);\n        }\n    }""",
    """    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n    public void onInventoryClick(InventoryClickEvent event) {\n        if (event.getInventory().getHolder() instanceof KitPreviewHolder) {\n            event.setCancelled(true);\n            return;\n        }\n        if (!(event.getWhoClicked() instanceof Player player) || !isVotingParticipant(player)) {\n            return;\n        }\n        ItemStack hotbar = event.getHotbarButton() >= 0\n                ? player.getInventory().getItem(event.getHotbarButton())\n                : null;\n        if (isKitVoteItem(event.getCurrentItem()) || isKitVoteItem(event.getCursor())\n                || isKitVoteItem(hotbar) || isKitVoteItem(player.getInventory().getItemInOffHand())) {\n            event.setCancelled(true);\n            player.updateInventory();\n        }\n    }\n\n    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n    public void onInventoryDrag(InventoryDragEvent event) {\n        if (!(event.getWhoClicked() instanceof Player player) || !isVotingParticipant(player)) {\n            return;\n        }\n        if (isKitVoteItem(event.getOldCursor())) {\n            event.setCancelled(true);\n            player.updateInventory();\n        }\n    }\n\n    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n    public void onSwapHands(PlayerSwapHandItemsEvent event) {\n        if (!isVotingParticipant(event.getPlayer())) {\n            return;\n        }\n        if (isKitVoteItem(event.getMainHandItem()) || isKitVoteItem(event.getOffHandItem())) {\n            event.setCancelled(true);\n            event.getPlayer().updateInventory();\n        }\n    }\n\n    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n    public void onDrop(PlayerDropItemEvent event) {\n        if (isVotingParticipant(event.getPlayer()) && isKitVoteItem(event.getItemDrop().getItemStack())) {\n            event.setCancelled(true);\n            event.getPlayer().updateInventory();\n        }\n    }\n\n    private boolean isVotingParticipant(Player player) {\n        EventSession session = eventManager.session();\n        return session != null && isKitVotingPhase(session) && isFightEvent(session.definition().type())\n                && eventManager.isEventPlayer(player.getUniqueId());\n    }\n\n    private boolean isKitVoteItem(ItemStack item) {\n        if (item == null || !item.hasItemMeta()) {\n            return false;\n        }\n        return item.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.STRING);\n    }""",
)

manager = "src/main/java/org/enthusia/events/event/EventManager.java"
replace_once(
    manager,
    """            List<CompletableFuture<Boolean>> teleports = new ArrayList<>();\n            List<Map.Entry<String, Location>> spawns = List.copyOf(map.spawns().entrySet());\n            int index = 0;""",
    """            List<CompletableFuture<Boolean>> teleports = new ArrayList<>();\n            List<Map.Entry<String, Location>> spawns = new ArrayList<>(map.spawns().entrySet());\n            if (session.definition().type() == EventType.FIGHT_FFA\n                    || session.definition().type() == EventType.SUMO_FFA) {\n                java.util.Collections.shuffle(spawns);\n            }\n            int index = 0;""",
)

gameplay = "src/main/java/org/enthusia/events/event/EventGameplayListener.java"
replace_once(
    gameplay,
    """            Block block = loc.getBlock();\n            if (block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA) {\n                block.setType(wool, false);\n                bedWarsPlacedBlocks.add(locationKey(block.getLocation()));\n            }\n        }, 0L, 2L); // Place every 2 ticks""",
    """            Block block = loc.getBlock();\n            if ((block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA)\n                    && !EventSessionHardeningListener.isBedWarsSpawnProtected(plugin, eventManager.activeMap(), block.getLocation())) {\n                block.setType(wool, false);\n                bedWarsPlacedBlocks.add(locationKey(block.getLocation()));\n            }\n        }, 0L, 2L); // Place every 2 ticks""",
)
replace_once(
    gameplay,
    """        for (int i = 0; i < 5; i++) {\n            Block block = loc.getBlock();\n            if (block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA) {\n                block.setType(wool, false);\n                bedWarsPlacedBlocks.add(locationKey(block.getLocation()));\n            }\n            loc.add(0, -1, 0);""",
    """        for (int i = 0; i < 5; i++) {\n            Block block = loc.getBlock();\n            if ((block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA)\n                    && !EventSessionHardeningListener.isBedWarsSpawnProtected(plugin, eventManager.activeMap(), block.getLocation())) {\n                block.setType(wool, false);\n                bedWarsPlacedBlocks.add(locationKey(block.getLocation()));\n            }\n            loc.add(0, -1, 0);""",
)
replace_once(
    gameplay,
    """    private void destroyBedWarsBedsOnTimer(EventSession session) {\n        if (bedWarsBedsDestroyedByTimer || eventManager.activeSecondsRemaining() > 300L) {\n            return;\n        }\n        bedWarsBedsDestroyedByTimer = true;\n        EventMap map = session.selectedMap();\n        if (map == null) {\n            return;\n        }\n        map.points().forEach((key, location) -> {\n            String lower = key.toLowerCase(Locale.ROOT);\n            if (!lower.startsWith(\"bed-\") || location == null || location.getWorld() == null) {\n                return;\n            }\n            String team = lower.substring(\"bed-\".length());\n            breakBedBlocksNear(location);\n            if (!brokenBedTeams.contains(team)) {\n                brokenBedTeams.add(team);\n            }\n            eventManager.setRuntimeScoreboardValue(\"bedwars-bed-\" + team, \"false\");\n        });\n        eventManager.messageEventPlayers(ChatColor.GOLD + \"[Events] \" + ChatColor.RED\n                + \"All beds have been destroyed. Every death is now final.\");\n        playForParticipants(session, Sound.ENTITY_WITHER_SPAWN, 0.8F, 1.2F);\n    }""",
    """    private void destroyBedWarsBedsOnTimer(EventSession session) {\n        if (bedWarsBedsDestroyedByTimer || eventManager.activeSecondsRemaining() > 300L) {\n            return;\n        }\n        bedWarsBedsDestroyedByTimer = true;\n        final boolean[] removedLiveBed = {false};\n        EventMap map = session.selectedMap();\n        if (map == null) {\n            return;\n        }\n        map.points().forEach((key, location) -> {\n            String lower = key.toLowerCase(Locale.ROOT);\n            if (!lower.startsWith(\"bed-\") || location == null || location.getWorld() == null) {\n                return;\n            }\n            String team = lower.substring(\"bed-\".length());\n            breakBedBlocksNear(location);\n            removedLiveBed[0] |= !brokenBedTeams.contains(team);\n            if (!brokenBedTeams.contains(team)) {\n                brokenBedTeams.add(team);\n            }\n            eventManager.setRuntimeScoreboardValue(\"bedwars-bed-\" + team, \"false\");\n        });\n        if (removedLiveBed[0]) {\n            eventManager.messageEventPlayers(ChatColor.GOLD + \"[Events] \" + ChatColor.RED\n                    + \"Deathmatch has started. All remaining beds were removed; every death is now final.\");\n            playForParticipants(session, Sound.ENTITY_WITHER_SPAWN, 0.8F, 1.2F);\n        }\n    }""",
)
replace_once(
    gameplay,
    """            case \"HARDENED_CLAY\" -> shopItem(player, Material.TERRACOTTA,\n                    16, ChatColor.GOLD + \"Hardened Clay\", 12, Material.IRON_INGOT,\n                    List.of(\"Basic block to defend your bed.\"), itemId, isQuickBuy);""",
    """            case \"HARDENED_CLAY\" -> shopItem(player, teamConcrete(player == null ? \"\" : eventManager.teamFor(player.getUniqueId())),\n                    16, ChatColor.GOLD + \"Concrete\", 12, Material.IRON_INGOT,\n                    List.of(\"Team-colored block to defend your bed.\"), itemId, isQuickBuy);""",
)
replace_once(
    gameplay,
    """    private Material teamWool(String team) {\n        return switch (team == null ? \"\" : team.toLowerCase(Locale.ROOT)) {\n            case \"1\", \"red\" -> Material.RED_WOOL;\n            case \"2\", \"blue\" -> Material.BLUE_WOOL;\n            case \"3\", \"green\" -> Material.GREEN_WOOL;\n            case \"4\", \"yellow\" -> Material.YELLOW_WOOL;\n            case \"5\", \"orange\" -> Material.ORANGE_WOOL;\n            case \"6\", \"purple\" -> Material.PURPLE_WOOL;\n            case \"7\", \"cyan\" -> Material.CYAN_WOOL;\n            default -> Material.WHITE_WOOL;\n        };\n    }""",
    """    private Material teamWool(String team) {\n        return switch (team == null ? \"\" : team.toLowerCase(Locale.ROOT)) {\n            case \"1\", \"red\" -> Material.RED_WOOL;\n            case \"2\", \"blue\" -> Material.BLUE_WOOL;\n            case \"3\", \"green\" -> Material.GREEN_WOOL;\n            case \"4\", \"yellow\" -> Material.YELLOW_WOOL;\n            case \"5\", \"orange\" -> Material.ORANGE_WOOL;\n            case \"6\", \"purple\" -> Material.PURPLE_WOOL;\n            case \"7\", \"cyan\" -> Material.CYAN_WOOL;\n            default -> Material.WHITE_WOOL;\n        };\n    }\n\n    private Material teamConcrete(String team) {\n        return switch (team == null ? \"\" : team.toLowerCase(Locale.ROOT)) {\n            case \"1\", \"red\" -> Material.RED_CONCRETE;\n            case \"2\", \"blue\" -> Material.BLUE_CONCRETE;\n            case \"3\", \"green\" -> Material.GREEN_CONCRETE;\n            case \"4\", \"yellow\" -> Material.YELLOW_CONCRETE;\n            case \"5\", \"orange\" -> Material.ORANGE_CONCRETE;\n            case \"6\", \"purple\" -> Material.PURPLE_CONCRETE;\n            case \"7\", \"cyan\" -> Material.CYAN_CONCRETE;\n            default -> Material.WHITE_CONCRETE;\n        };\n    }""",
)

print("Applied staged production retest fixes.")
