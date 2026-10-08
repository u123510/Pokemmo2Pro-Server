package org.pokemmo.gameserver.game.gtl;
public enum GtlFilterType {
    NEWEST(0),
    EARLIEST(1),
    LOWEST_PRICE(2),
    HIGHEST_PRICE(3);
    private static GtlFilterType[] allTypeArray = new GtlFilterType[]{NEWEST, EARLIEST, LOWEST_PRICE, HIGHEST_PRICE};
    public static GtlFilterType getByType(int type) {
        for(GtlFilterType gtlFilterType:allTypeArray){
            if(gtlFilterType.type == (byte)type){
                return gtlFilterType;
            }
        }
        return null;
    }
    private byte type;
    public byte getType() {
        return type;
    }
    GtlFilterType(int type) {
        this.type = (byte) type;
    }
}
