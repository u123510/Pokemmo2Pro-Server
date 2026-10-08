package org.pokemmo.gameserver.game.map;

public enum MapConnectionType {
    NOTHING(0,"Nothing"),
    DOWN(1,"Down"),
    UP(2,"Up"),
    LEFT(3,"Left"),
    RIGHT(4,"Right"),
    DIVE(5,"Dive"),
    EMERGE(6,"Emerge");
    private byte type;
    private String name;
    private static final MapConnectionType[] allTypeArray = values();
    MapConnectionType(int type, String name) {
        this.type = (byte)type;
        this.name = name;
    }
    public byte getType() {
        return type;
    }
    public String getName() {
        return name;
    }
    public static MapConnectionType getByType(int type) {
       return allTypeArray[type];
    }
}
