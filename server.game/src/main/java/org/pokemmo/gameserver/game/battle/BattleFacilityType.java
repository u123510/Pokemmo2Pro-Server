package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.backgroundmusic.BgType;

public enum BattleFacilityType {
    GRASS(0, BgType.BG04),
    GRASS_DARK(1, BgType.BG04),
    SAND(2, BgType.BG11),
    UNDERWATER(3, BgType.BG13),
    WATER(4, BgType.BG09),
    WATER_CALM(5, BgType.BG09),
    DIRT(6, BgType.BG08),
    CAVE(7, BgType.BG08),
    ROCK(8, BgType.BG05),
    ROUTE(9, BgType.BG04),
    INSIDE(12, BgType.BG10);
    private byte type;
    private BgType bgType;
    BattleFacilityType(int type, BgType bgType) {
        this.type = (byte)type;
        this.bgType = bgType;
    }
    public byte getType() {
        return type;
    }
    public BgType getBgType() {
        return bgType;
    }
}
