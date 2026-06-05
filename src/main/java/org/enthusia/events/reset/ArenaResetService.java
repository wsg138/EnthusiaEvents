package org.enthusia.events.reset;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.CuboidRegion;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class ArenaResetService implements Listener {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final Map<String, BlockState> originalStates = new LinkedHashMap<>();

    public ArenaResetService(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    public void reset() {
        List<BlockState> states = originalStates.values().stream().toList().reversed();
        originalStates.clear();
        int limit = plugin.getConfig().getInt("reset.max-blocks-per-session", 50_000);
        if (states.size() > limit) {
            plugin.getLogger().warning("Skipping arena reset because " + states.size()
                    + " tracked blocks exceeds reset.max-blocks-per-session (" + limit + ").");
            return;
        }
        for (BlockState state : states) {
            state.update(true, false);
        }
    }

    public void clear() {
        originalStates.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (trackCurrentSession(event.getBlockPlaced().getLocation())) {
            remember(event.getBlockReplacedState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            record(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        record(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!plugin.getConfig().getBoolean("reset.track-physics", false)) {
            return;
        }
        record(event.getBlock());
    }

    private void record(Block block) {
        if (trackCurrentSession(block.getLocation())) {
            remember(block.getState());
        }
    }

    private void remember(BlockState state) {
        originalStates.putIfAbsent(key(state.getLocation()), state);
    }

    private boolean trackCurrentSession(Location location) {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE || !isResetTracked(session.definition().type())) {
            return false;
        }
        EventMap map = session.selectedMap();
        CuboidRegion region = map == null ? null : map.region();
        return region != null && region.contains(location);
    }

    private boolean isResetTracked(EventType type) {
        return type == EventType.SPLEEF
                || type == EventType.SPLEEG
                || type == EventType.BEDWARS
                || type == EventType.SKYWARS
                || type == EventType.BLOCK_PARTY;
    }

    private String key(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":"
                + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
