package org.pokemmo.gameserver.game.building;

public enum BulidingType {
    SECRET_BASE(0),
    HOUSE(1);
    private byte type;
    public static final BulidingType[] allTypeArray = {SECRET_BASE,HOUSE};
    BulidingType(int type) {
        this.type = (byte) type;
    }
    public static BulidingType getBulidingType(int type){
        for(BulidingType bulidingType:allTypeArray){
            if(bulidingType.type == (byte)type){
                return bulidingType;
            }
        }
        return null;
    }
    public byte getType() {
        return type;
    }

}
