package org.pokemmo.gameserver.game.map;

import java.util.ArrayList;
import java.util.List;

public class SinnohMapConfig {
    int regionId;
    int bankId;
    int mapId;
    String name = "";
    int width;
    int height;
    int unknownShort;
    int unknownByte;
    String lighting = "REGULAR";
    String weather = "GEN4_NONE";
    String mapType = "INSIDE";
    String encounterType = "RANDOM";
    List<String> terrainBehaviors = new ArrayList<>();
    List<OpenMmoMapConfig.EncounterFieldConfig> encounterFields = new ArrayList<>();
    List<OpenMmoMapConfig.WildEncounterConfig> wildEncounters = new ArrayList<>();
    List<OpenMmoMapConfig.WarpConfig> warps = new ArrayList<>();
    List<OpenMmoMapConfig.NpcConfig> npcs = new ArrayList<>();
    String onTransitionScript = "";
    List<OpenMmoMapConfig.FrameScriptConfig> onFrameScripts = new ArrayList<>();
    List<OpenMmoMapConfig.CoordScriptConfig> coordScripts = new ArrayList<>();
    List<OpenMmoMapConfig.BgEventConfig> bgEvents = new ArrayList<>();
    TerrainConfig terrain;

    static class TerrainConfig {
        int cols;
        int rows;
        int chunkSide;
        List<String> chunks = new ArrayList<>();
        List<Integer> altitudes = new ArrayList<>();
        List<Integer> headers = new ArrayList<>();
    }
}
