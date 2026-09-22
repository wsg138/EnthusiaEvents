package org.enthusia.events.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

final class CuboidRegionTest {
    @Test
    void fromCornersNormalizesBlockCoordinatesRegardlessOfSelectionOrder() {
        World world = world("event-map", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Location a = new Location(world, 10.9, 70.1, -3.1);
        Location b = new Location(world, 5.2, 64.9, 2.8);

        CuboidRegion region = CuboidRegion.fromCorners(a, b);

        assertEquals("event-map", region.worldName());
        assertEquals(5.0, region.minX());
        assertEquals(64.0, region.minY());
        assertEquals(-4.0, region.minZ());
        assertEquals(10.0, region.maxX());
        assertEquals(70.0, region.maxY());
        assertEquals(2.0, region.maxZ());
    }

    @Test
    void containsIncludesWholeMaximumBlockButExcludesNextBlockAndWrongWorld() {
        World eventWorld = world("event-map", UUID.randomUUID());
        World otherWorld = world("survival", UUID.randomUUID());
        CuboidRegion region = new CuboidRegion("event-map", 0, 10, 20, 2, 12, 22);

        assertTrue(region.contains(new Location(eventWorld, 0.0, 10.0, 20.0)));
        assertTrue(region.contains(new Location(eventWorld, 2.999, 12.999, 22.999)));
        assertFalse(region.contains(new Location(eventWorld, 3.0, 12.0, 22.0)));
        assertFalse(region.contains(new Location(eventWorld, 2.0, 13.0, 22.0)));
        assertFalse(region.contains(new Location(eventWorld, 2.0, 12.0, 23.0)));
        assertFalse(region.contains(new Location(otherWorld, 1.0, 11.0, 21.0)));
        assertFalse(region.contains(null));
    }

    @Test
    void fromCornersRejectsMissingOrDifferentWorlds() {
        World first = world("event-map", UUID.fromString("00000000-0000-0000-0000-000000000011"));
        World second = world("event-map-copy", UUID.fromString("00000000-0000-0000-0000-000000000012"));

        assertThrows(IllegalArgumentException.class, () -> CuboidRegion.fromCorners(null, new Location(first, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> CuboidRegion.fromCorners(new Location(null, 0, 0, 0), new Location(first, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> CuboidRegion.fromCorners(new Location(first, 0, 0, 0), new Location(second, 1, 1, 1)));
    }

    private static World world(String name, UUID uid) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUID" -> uid;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "World[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
