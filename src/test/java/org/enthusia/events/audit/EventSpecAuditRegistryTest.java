package org.enthusia.events.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.enthusia.events.event.EventType;
import org.junit.jupiter.api.Test;

final class EventSpecAuditRegistryTest {
    @Test
    void everyRegisteredEventTypeHasACompleteAuditSpecification() {
        EventSpecAuditRegistry registry = new EventSpecAuditRegistry(null, null, null);

        for (EventType type : EventType.values()) {
            EventSpecAuditRegistry.EventSpec spec = registry.spec(type);
            assertNotNull(spec, type + " must have an event-spec audit entry");
            assertFalse(spec.setup().isBlank(), type + " setup requirements must be documented");
            assertFalse(spec.winCondition().isBlank(), type + " win condition must be documented");
            assertFalse(spec.resetBehavior().isBlank(), type + " reset behavior must be documented");
            String summary = spec.summary();
            assertTrue(summary.contains("setup="));
            assertTrue(summary.contains("kits="));
            assertTrue(summary.contains("loot="));
            assertTrue(summary.contains("win="));
            assertTrue(summary.contains("reset="));
        }
    }

    @Test
    void kitAndLootContractsForRepresentativeEventFamiliesStayExplicit() {
        EventSpecAuditRegistry registry = new EventSpecAuditRegistry(null, null, null);

        var skyWars = registry.spec(EventType.SKYWARS);
        assertTrue(skyWars.usesKits());
        assertTrue(skyWars.usesLoot());

        var fight = registry.spec(EventType.FIGHT_1V1);
        assertTrue(fight.usesKits());
        assertFalse(fight.usesLoot());

        var sumo = registry.spec(EventType.SUMO_1V1);
        assertFalse(sumo.usesKits());
        assertFalse(sumo.usesLoot());
    }
}
