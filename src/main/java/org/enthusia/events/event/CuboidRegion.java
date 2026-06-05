package org.enthusia.events.event;

import org.bukkit.Location;
import org.bukkit.World;

public record CuboidRegion(
        String worldName,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {

    public static CuboidRegion fromCorners(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            throw new IllegalArgumentException("Both corners must be in a world.");
        }
        if (!a.getWorld().getUID().equals(b.getWorld().getUID())) {
            throw new IllegalArgumentException("Region corners must be in the same world.");
        }
        return new CuboidRegion(
                a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ())
        );
    }

    public boolean contains(Location location) {
        if (location == null) {
            return false;
        }
        World world = location.getWorld();
        if (world == null || !world.getName().equals(worldName)) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
