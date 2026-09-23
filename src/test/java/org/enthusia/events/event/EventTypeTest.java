package org.enthusia.events.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class EventTypeTest {
    @Test
    void parsesEveryCanonicalEventNameCaseInsensitively() {
        for (EventType type : EventType.values()) {
            assertEquals(type, EventType.parse(type.name()));
            assertEquals(type, EventType.parse(type.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Test
    void acceptsLegacySpleegSpellingAsSplegg() {
        assertEquals(EventType.SPLEGG, EventType.parse("spleeg"));
        assertEquals(EventType.SPLEGG, EventType.parse("SPLEEG"));
    }

    @Test
    void invalidNullBlankAndWhitespaceWrappedValuesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> EventType.parse(null));
        assertThrows(IllegalArgumentException.class, () -> EventType.parse(""));
        assertThrows(IllegalArgumentException.class, () -> EventType.parse("not-an-event"));
        assertThrows(IllegalArgumentException.class, () -> EventType.parse(" skywars "));
    }
}
