package org.pokemmo.gameserver.game.map;

/**
 * Runtime switches for map content. NPC placement remains enabled by default,
 * while script-backed map events are disabled until the new map pipeline is
 * fully wired.
 */
public final class MapLoadingOptions {
    private static final String NPCS_PROPERTY = "openmmo.map.npcs.enabled";
    private static final String EVENTS_PROPERTY = "openmmo.map.events.enabled";
    private static final String NPCS_ENV = "OPENMMO_MAP_NPCS_ENABLED";
    private static final String EVENTS_ENV = "OPENMMO_MAP_EVENTS_ENABLED";

    private MapLoadingOptions() {
    }

    public static boolean areNpcsEnabled() {
        return readBoolean(NPCS_PROPERTY, NPCS_ENV, true);
    }

    public static boolean areEventsEnabled() {
        return readBoolean(EVENTS_PROPERTY, EVENTS_ENV, false);
    }

    private static boolean readBoolean(String property, String environment, boolean defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        return defaultValue;
    }
}
