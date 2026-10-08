package org.pokemmo.gameserver.game.map;

import org.pokemmo.gameserver.game.battle.BattleWeatherType;

public enum MapWeatherType {
    IN_HOUSE_WEATHER(0,0,-1,BattleWeatherType.NORMAL,"In_House_Weather"),
    SUNNY_WEATHER_WITH_CLOUDS_IN_WATER(1, 1,-1, null,"Sunny_Weather__Clouds_In_Water"),
    REGULAR_WEATHER(2, 2,-1, null,"Regular_Weather"),
    RAINY_WEATHER(3, 3,-1, BattleWeatherType.SHITTY_RAIN,"Rainy_Weather"),
    THREE_SNOW_FLAKES(4, 4,-1, BattleWeatherType.FOG,"Three_Snow_Flakes"),
    RAIN_WITH_THUNDER(5, 5,-1, BattleWeatherType.SHITTY_RAIN,"Rain_Weather_With_Thunder"),
    STEADY_MIST(6,6,-1,BattleWeatherType.NORMAL,"Steady_Mist"),
    STEADY_SNOW(7,7,-1,BattleWeatherType.NORMAL,"Steady_Snow"),
    SAND_STORM(8, 8,-1, BattleWeatherType.SANDSTORM,"Sandstorm"),
    MIST_FROM_TOP_RIGHT(9,9,-1,BattleWeatherType.NORMAL,"Mist_From_Top_Right"),
    DENSE_BRIGHT_MIST(10,10,-1,BattleWeatherType.NORMAL,"Dense_Bright_Mist"),
    CLOUDY(11, 11,-1, null,"Cloudy"),
    UNDERGROUND_FLASHES(12,12,-1,BattleWeatherType.NORMAL,"Underground_Flashes"),
    HEAVY_RAIN_WITH_THUNDER(13, 13,-1, BattleWeatherType.SHITTY_RAIN,"Heavy_Rain_Weather_With_Thunder"),
    UNDERWATER_MIST(14,14,-1,BattleWeatherType.NORMAL,"Underwater_Mist"),
    UNKNOWN_THUNDER(15, 15,-1, BattleWeatherType.SHITTY_RAIN,"Unknown_Thunder"),
    DAY_DEPENDANT(16,19,-1,BattleWeatherType.NORMAL,"Day_Dependant"),
    CUSTOM_SNOW(17,32,-1,BattleWeatherType.NORMAL,"Custom_Snow"),
    GEN4_NONE(18,40, 0, null,"Gen4_None"),
    GEN4_UNK0(19,41, 1, BattleWeatherType.NORMAL,"Gen4_Unknown0"),
    GEN4_RAIN(20,42, 2, BattleWeatherType.SHITTY_RAIN,"Gen4_Rain"),
    GEN4_HEAVY_RAIN(21,43, 3, BattleWeatherType.SHITTY_RAIN,"Gen4_Heavy_Rain"),
    GEN4_HEAVY_RAIN_WITH_THUNDER(22,44, 4, BattleWeatherType.SHITTY_RAIN,"Gen4_Heavy_Rain_Weather_With_Thunder"),
    GEN4_SNOW(23,45, 5, BattleWeatherType.SHITTY_RAIN,"Gen4_Snow"),
    GEN4_HEAVY_SNOW(24,46, 6, BattleWeatherType.SHITTY_RAIN,"Gen4_Heavy_Snow"),
    GEN4_HAIL(25,47, 7, BattleWeatherType.SNOW,"Gen4_Hail"),
    GEN4_CLEAR(26,48, 8, BattleWeatherType.NORMAL,"Gen4_Clear"),
    GEN4_ASHDUST(27,49, 9, BattleWeatherType.NORMAL,"Gen4_Ashdust"),
    GEN4_SANDSTORM(28,50, 10, BattleWeatherType.SANDSTORM,"Gen4_Sandstorm"),
    GEN4_SPECIAL_ICY(29,51, 11, BattleWeatherType.NORMAL,"Gen4_Special_Icy"),
    GEN4_SPECIAL_ROCKS(30,52, 12, BattleWeatherType.NORMAL,"Gen4_Special_Rocks"),
    GEN4_UNK(31,53, 13, BattleWeatherType.NORMAL,"Gen4_Unknown0"),
    GEN4_HEAVY_FOG(32,54, 14, BattleWeatherType.FOG,"Gen4_Heavy_Fog"),
    GEN4_HEAVY_FOG_WITH_DARKNESS(33,55, 15, BattleWeatherType.NORMAL,"Gen4_Heavy_Fog_With_Darkness"),
    GEN4_CAVE_FLASH(34,56, 16, BattleWeatherType.NORMAL,"Gen4_Cave_Flash"),
    GEN4_FOREST_TREE_SHADOWS(35,57, 23, BattleWeatherType.NORMAL,"Gen4_Forest_Tree_Shadows"),
    GEN4_DARKNESS(36,58, 26, BattleWeatherType.NORMAL,"Gen4_Darkness"),
    GEN4_GREEN_HAZE(37,59, 27, BattleWeatherType.NORMAL,"Gen4_Green_Haze"),
    GEN4_RED_HAZE(38,60,28, BattleWeatherType.NORMAL,"Gen4_Red_Haze"),
    GEN4_BLUE_HAZE(39,61,29,BattleWeatherType.NORMAL,"Gen4_Blue_Haze"),
    GEN4_BLACK_HAZE(40,62,30,BattleWeatherType.NORMAL,"Gen4_Black_Haze"),
    GEN4_RAIN2(41,63,32,BattleWeatherType.SHITTY_RAIN,"Gen4_Rain2"),
    GEN4_UNK2(42,64,33,BattleWeatherType.NORMAL,"Gen4_Unknown2"),
    GEN4_HEAVY_SNOW2(43,65,34,BattleWeatherType.NORMAL,"Gen4_Heavy_Snow2"),
    GEN4_HEAVY_SNOW3(44,66,35,BattleWeatherType.NORMAL,"Gen4_Heavy_Snow3"),
    GEN4_SNOW2(45,67,36,BattleWeatherType.NORMAL,"Gen4_Snow2");
    private final byte index;
    private final byte gameType;
    private final int unk2;
    private final BattleWeatherType battleWeatherType;
    private final String name;

    private static MapWeatherType[] allTypeArray = values();
    public byte getIndex() {
        return index;
    }
    public byte getGameType() {
        return gameType;
    }
    public int getUnk2() {
        return unk2;
    }
    public BattleWeatherType getBattleWeatherType() {
        return battleWeatherType;
    }
    public String getName() {
        return name;
    }
    MapWeatherType(int index, int gameType, int unk2, BattleWeatherType battleWeatherType,String name) {
        this.index = (byte) index;
        this.gameType = (byte) gameType;
        this.unk2 = unk2;
        this.battleWeatherType = battleWeatherType;
        this.name = name;
    }
    public static MapWeatherType getByIndex(int index){
        return allTypeArray[index];
    }
    public static MapWeatherType getByGameType(int gameType){
        for(MapWeatherType type : allTypeArray){
            if(type.getGameType() == gameType){
                return type;
            }
        }
        return null;
    }
    public static MapWeatherType getByName(String name){
        for(MapWeatherType type : allTypeArray){
            if(type.name.equals(name)){
                return type;
            }
        }
        return null;
    }
}
