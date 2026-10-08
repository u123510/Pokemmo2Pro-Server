package org.pokemmo.gameserver.game.battle;

public enum BattlePokemonCommandType {
    NULL(-1),
    MOVE(0),
    ITEM(1),
    SWAP(2),
    RUN(3),
    RECHARGE(4),
    SAFARI_BALL(5),
    SAFARI_BAIT(6),
    SAFARI_ROCK(7),
    SAFARI_WILD_MONSTER(8),
    NATURE_BATTLE_INCAPABLE_ACTION(9),
    SKIP(10),
    SHIFT(11),
    DISOBEDIENCE(12),
    FORFEIT(13),
    ROTATE(14),
    FREE_SHIFT(15),
    COWER(16),
    UNK(17),
    UNK2(18);
    private byte type;
    private static BattlePokemonCommandType[] allTypeArray = values();
    public static BattlePokemonCommandType getByType(byte type){
        return allTypeArray[type+1];
    }
    BattlePokemonCommandType(int type){
        this.type = (byte)type;
    }
    public byte getType(){
        return type;
    }
}
