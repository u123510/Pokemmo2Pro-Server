package org.pokemmo.gameserver.game.battle;

public enum BattleSpikeType {
    SPIKES(0,3),
    TOXIC_SPIKES(1,2),
    STEALTH_ROCK_SPIKES(2,1),
    UNK_SPIKES_0(3,3),
    UNK_SPIKES_1(4,2),
    UNK_SPIKES_2(5,2),
    UNK_SPIKES_3(6,2),
    UNK_SPIKES_4(7,3),
    UNK_SPIKES_5(8,1);
    private byte type;
    private byte maxAmount;
    private static BattleSpikeType[] allTypeArray = values();
    BattleSpikeType(int type, int maxAmount){
        this.type = (byte)type;
        this.maxAmount = (byte) maxAmount;
    }
    public static BattleSpikeType getByType(int type){
        return allTypeArray[type];
    }
    public byte getType(){
        return type;
    }
    public byte getMaxAmount(){
        return maxAmount;
    }
}
