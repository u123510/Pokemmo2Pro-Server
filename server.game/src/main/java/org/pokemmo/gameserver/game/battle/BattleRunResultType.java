package org.pokemmo.gameserver.game.battle;

public enum BattleRunResultType {
    NORMAL_SUCCESS(0),
    NORMAL_FAIL(1),
    HORDE_CANT_ESCAPE(2),
    ITEM_GOT_AWAY(3),
    ABILITY_GOT_AWAY(4),
    FORFEIT(5);
    private byte type;
    private static BattleRunResultType[] allTypeArray = values();
    BattleRunResultType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static BattleRunResultType getByType(int type){
        return allTypeArray[type];
    }
}
