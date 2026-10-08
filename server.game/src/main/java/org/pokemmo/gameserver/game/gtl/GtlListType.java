package org.pokemmo.gameserver.game.gtl;
public enum GtlListType {
    MONSTER(0),
    ITEM(1),
    OWN_LISTINGS(2);
    private static final GtlListType[] allTypesArray = new GtlListType[]{MONSTER, ITEM, OWN_LISTINGS};
    private byte type;
    private GtlListType(int type) {
        this.type = (byte) type;
    }
    public static GtlListType getListType(int type) {
        for(GtlListType gtlListType:allTypesArray){
            if(gtlListType.type == (byte)type){
                return gtlListType;
            }
        }
        return null;
    }
    public byte getType() {
        return type;
    }
}
