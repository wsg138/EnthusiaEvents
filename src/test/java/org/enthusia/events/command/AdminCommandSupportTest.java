package org.enthusia.events.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.enthusia.events.event.EventType;
import org.junit.jupiter.api.Test;

final class AdminCommandSupportTest {
    @Test
    void filterIsCaseInsensitivePrefixMatchAndPreservesInputOrder() {
        List<String> values = List.of("SkyWars", "Spleef", "BedWars", "Splegg");

        assertEquals(List.of("SkyWars"), AdminCommandSupport.filter("sky", values));
        assertEquals(List.of("Spleef", "Splegg"), AdminCommandSupport.filter("SP", values));
        assertEquals(values, AdminCommandSupport.filter("", values));
        assertEquals(List.of(), AdminCommandSupport.filter("quake", values));
    }

    @Test
    void positiveIntParserClampsZeroAndNegativeAndUsesFallbackForMalformedInput() {
        assertEquals(7, AdminCommandSupport.parsePositiveInt("7", 99));
        assertEquals(1, AdminCommandSupport.parsePositiveInt("0", 99));
        assertEquals(1, AdminCommandSupport.parsePositiveInt("-4", 99));
        assertEquals(99, AdminCommandSupport.parsePositiveInt("abc", 99));
        assertEquals(99, AdminCommandSupport.parsePositiveInt("", 99));
    }

    @Test
    void silentEventParserSupportsCanonicalAndLegacyNamesAndRejectsInvalidInput() {
        assertEquals(EventType.SKYWARS, AdminCommandSupport.parseEventSilently("skywars"));
        assertEquals(EventType.SPLEGG, AdminCommandSupport.parseEventSilently("spleeg"));
        assertNull(AdminCommandSupport.parseEventSilently("unknown"));
        assertNull(AdminCommandSupport.parseEventSilently(null));
    }
}
