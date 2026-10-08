package org.pokemmo.gameserver.game.map;

public enum MapLightingType {
    REGULAR(0,"Regular"),
    DARK_FLASH_USABLE(1,"DarkFlashUsable"),
    DARK_FLASH_UNUSABLE(2,"DarkFlashUnusable");
    private byte type;
    private static MapLightingType[] allTypeArray = {REGULAR, DARK_FLASH_USABLE, DARK_FLASH_UNUSABLE};
    private String name;
    MapLightingType(int type,String name) {
        this.type = (byte) type;
        this.name = name;
    }
    public byte getType() {
        return type;
    }
    public String getName() {
        return name;
    }
    public static MapLightingType getByType(int type) {
        return allTypeArray[type];
    }
    public static MapLightingType getByName(String name){
        for(MapLightingType type : allTypeArray){
            if(type.name.equals(name)){
                return type;
            }
        }
        return null;
    }
}
