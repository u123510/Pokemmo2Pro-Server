package org.pokemmo.gameserver.game.battle;

public enum BattleWeatherType {
    NORMAL(0,false),
    SUNNY(1,false),
    RAIN(2,true),
    SANDSTORM(3,false),
    FOG(4,false),
    SNOW(5,false),
    SHITTY_RAIN(6,true),
    EVENT_HALLOWEEN_STORM(7,true),
    EVENT_CNY_DARKNESS(8,false);

    private byte type;
    private final boolean unk;

    BattleWeatherType(int type, boolean unk) {
        this.type = (byte)type;
        this.unk = unk;
    }

    public byte getType() {
        return type;
    }
}
