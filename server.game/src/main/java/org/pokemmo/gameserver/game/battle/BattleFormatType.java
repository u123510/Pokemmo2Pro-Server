package org.pokemmo.gameserver.game.battle;

//对战的形式类型
public enum BattleFormatType {
    SINGLE_BATTLE(0,1,1),
    DOUBLE_BATTLE(1,2,2),
    MULTI_BATTLE(2,2,2),
    MULTI_BATTLE_PVP(3,2,2),
    DUMMY_BATTLE(4,1,1),
    TRIPLE_BATTLE(5,3,3),
    HORDE_BATTLE(6,1,5),
    ROTATION_BATTLE(7,1,1),
    RAID_BATTLE(8,4,3);
    private static BattleFormatType[] allTypeArray = values();
    private byte type;
    private byte firstFactionMaxDebutPokemonAmount;
    private byte secondFactionMaxDebutPokemonAmount;
    private boolean canRun;
    private boolean canUseItem;
    private byte canUseItemAmount;
    BattleFormatType(int type, int firstFactionMaxDebutPokemonAmount, int secondFactionMaxDebutPokemonAmount) {
        this.type = (byte) type;
        this.firstFactionMaxDebutPokemonAmount = (byte) firstFactionMaxDebutPokemonAmount;
        this.secondFactionMaxDebutPokemonAmount = (byte) secondFactionMaxDebutPokemonAmount;
    }
    public byte getType() {
        return type;
    }
    public byte getFirstFactionMaxDebutPokemonAmount() {
        return firstFactionMaxDebutPokemonAmount;
    }
    public byte getSecondFactionMaxDebutPokemonAmount() {
        return secondFactionMaxDebutPokemonAmount;
    }
    public static BattleFormatType getByType(byte index){
        return allTypeArray[index];
    }
}
