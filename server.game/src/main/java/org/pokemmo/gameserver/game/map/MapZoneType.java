package org.pokemmo.gameserver.game.map;

public enum MapZoneType {
    UNKNOWN_0x00(0,false,"Unknown"),
    VILLAGE(1,true,"Village"),
    CITY(2,true,"City"),
    ROUTE(3,true,"Route"),
    UNDERGROUND(4,false,"Underground"),
    UNDERWATER(5,false,"Underwater"),
    UNKNOWN_0x06(6,true,"Unknown"),
    UNKNOWN_0x07(7,false,"Unknown"),
    INSIDE(8,false,"Inside"),
    SECRET_BASE(9,false,"Secret Base");

    private final boolean isOutSide;
    private final byte type;
    private final String name;
    private static MapZoneType[] allTypeArray = values();
    MapZoneType(int type, boolean isOutSide,String name) {
        this.type = (byte) type;
        this.isOutSide = isOutSide;
        this.name = name;
    }
    public byte getType() {
        return type;
    }
    public boolean isOutSide() {
        return isOutSide;
    }
    public String getName() {
        return name;
    }
    public static MapZoneType getByType(int type){
        return allTypeArray[type];
    }
    public static MapZoneType getByName(String name){
        for(MapZoneType type : allTypeArray){
            if(type.name.equals(name)){
                return type;
            }
        }
        return null;
    }
}
