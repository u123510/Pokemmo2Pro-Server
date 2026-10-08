package org.pokemmo.gameserver.game.item;

public enum ItemRarityType {
    NORMAL(-1),
    COMMON(0),
    PREMIUM(1),
    LEGENDARY(2);
    private byte type;
    private static final ItemRarityType[] allTypeArray = {NORMAL, COMMON, PREMIUM, LEGENDARY};
    ItemRarityType(int type) {
        this.type = (byte) type;
    }
    public static ItemRarityType getByType(int type){
        for(ItemRarityType itemRarityType:allTypeArray){
            if(itemRarityType.type == (byte)type){
                return itemRarityType;
            }
        }
        return null;
    }
    public byte getType(){
        return type;
    }
}
