package org.pokemmo.gameserver.game.map;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON shape emitted by the openmmo老外 map generator.
 *
 * The fields intentionally mirror its MapDef model rather than the legacy
 * server JSON. This lets both resource formats coexist during migration.
 */
public class OpenMmoMapConfig {
    int regionId;
    int bankId;
    int mapId;
    int width = 20;
    int height = 15;
    int paletteIdx1 = 12;
    int paletteIdx2 = 14;
    int borderWidth = 2;
    int borderHeight = 2;
    int unknownShort;
    int unknownByte;
    String lighting = "REGULAR";
    String weather = "REGULAR_WEATHER";
    String mapType = "CITY";
    String encounterType = "RANDOM";
    String blockData = "";
    String behaviorData = "";
    // Server-side metadata absent from the generator's simplified behavior ordinals.
    List<InteractionCounterConfig> interactionCounters = new ArrayList<>();
    List<EncounterFieldConfig> encounterFields = new ArrayList<>();
    List<WildEncounterConfig> wildEncounters = new ArrayList<>();
    String onTransitionScript = "";
    List<FrameScriptConfig> onFrameScripts = new ArrayList<>();
    List<CoordScriptConfig> coordScripts = new ArrayList<>();
    List<BgEventConfig> bgEvents = new ArrayList<>();
    List<TileConfig> borderTiles = new ArrayList<>();
    List<ConnectionConfig> connections = new ArrayList<>();
    List<WarpConfig> warps = new ArrayList<>();
    List<NpcConfig> npcs = new ArrayList<>();

    public static class TileConfig {
        int material;
        int collision;
    }

    public static class InteractionCounterConfig {
        int x = -1;
        int y = -1;
    }

    public static class ConnectionConfig {
        String direction;
        int unknown;
        int targetBank;
        int targetMap;
    }

    public static class WarpConfig {
        int x;
        int y;
        int elevation;
        int targetRegionId;
        int targetBankId;
        int targetMapId;
        int targetX;
        int targetY;
        int targetElevation;
        String facingDirection;
        String exitFacing;
        boolean dynamic;
    }

    public static class NpcConfig {
        int entityIdx;
        int graphicsId;
        int x;
        int y;
        int elevation;
        String movementType = "NONE";
        int movementRangeX;
        int movementRangeY;
        int trainerType;
        String facing = "DOWN";
        String script = "0x0";
        String shopId;
        String hideFlag = "";
        int rawMovementType = -1;
        int rawFacing = -1;
    }

    public static class EncounterFieldConfig {
        String type;
        int[] encounter_rates;
        java.util.Map<String, int[]> groups;
    }

    public static class WildEncounterConfig {
        String map;
        String base_label;
        EncounterTableConfig land_mons;
        EncounterTableConfig water_mons;
        EncounterTableConfig rock_smash_mons;
        EncounterTableConfig fishing_mons;
    }

    public static class EncounterTableConfig {
        int encounter_rate;
        List<EncounterSlotConfig> mons = new ArrayList<>();
    }

    public static class EncounterSlotConfig {
        int min_level;
        int max_level;
        String species;
    }

    public static class FrameScriptConfig {
        String varKey;
        int value;
        String script;
    }

    public static class CoordScriptConfig {
        int x;
        int y;
        int elevation;
        String varKey;
        int value;
        String script;
    }

    public static class BgEventConfig {
        int x;
        int y;
        int elevation;
        String facingDir;
        String script;
    }
}
