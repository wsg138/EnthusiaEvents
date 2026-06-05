package org.enthusia.events.trophy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.LocationCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PodiumService {

    private final EnthusiaEventsPlugin plugin;
    private final List<Location> activeHeads = new ArrayList<>();

    public PodiumService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(List<UUID> rankings) {
        clear();
        List<String> paths = List.of("locations.podium.first", "locations.podium.second", "locations.podium.third");
        for (int index = 0; index < Math.min(3, rankings.size()); index++) {
            Location location = LocationCodec.decode(plugin.getConfig().getString(paths.get(index), ""));
            if (location == null || location.getWorld() == null) {
                continue;
            }
            placeHead(location, rankings.get(index));
        }
    }

    public void clear() {
        for (Location location : activeHeads) {
            if (location != null && location.getWorld() != null && location.getBlock().getType() == Material.PLAYER_HEAD) {
                location.getBlock().setType(Material.AIR, false);
            }
        }
        activeHeads.clear();
    }

    private void placeHead(Location location, UUID uuid) {
        Block block = location.getBlock();
        block.setType(Material.PLAYER_HEAD, false);
        if (block.getState() instanceof Skull skull) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            skull.setOwningPlayer(player);
            skull.update(true, false);
        }
        activeHeads.add(location.clone());
    }
}
