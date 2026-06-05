package org.enthusia.events.setup;

import org.enthusia.events.event.EventType;

public record SetupSession(EventType eventType, String mapId, SetupTool tool, String value) {
}
