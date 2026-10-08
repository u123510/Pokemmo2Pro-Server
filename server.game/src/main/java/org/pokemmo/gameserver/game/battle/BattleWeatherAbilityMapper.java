package org.pokemmo.gameserver.game.battle;

import java.util.HashMap;
import java.util.Map;
public class BattleWeatherAbilityMapper {
    public static final Map<Short, BattleWeatherType> ABILITY_WEATHER_MAP = new HashMap<>();
    static {
        ABILITY_WEATHER_MAP.put((short)2, BattleWeatherType.RAIN);
        ABILITY_WEATHER_MAP.put((short)45, BattleWeatherType.SANDSTORM);
        ABILITY_WEATHER_MAP.put((short)70, BattleWeatherType.SUNNY);
        ABILITY_WEATHER_MAP.put((short)76, BattleWeatherType.NORMAL);
        ABILITY_WEATHER_MAP.put((short)117, BattleWeatherType.SNOW);
    }
}
