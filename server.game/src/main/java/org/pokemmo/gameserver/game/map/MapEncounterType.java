package org.pokemmo.gameserver.game.map;

public enum MapEncounterType {
    RANDOM(0,"Random"),
    GYM_STYLE(1,"GymStyle"),
    VILLIAN_TEAM_STYLE(2,"VillianTeamStyle"),
    UNKNOWN_0x03(3,"Unknown0x03"),
    TOP_4_1(4,"Top4_1"),
    TOP_4_2(5,"Top4_2"),
    TOP_4_3(6,"Top4_3"),
    TOP_4_4(7,"Top4_4"),
    BIG_RED_BALL(8,"BigRedBall");
    private byte type;
    private String name;
    private static final MapEncounterType[] allTypeArray = values();
    MapEncounterType(int type,String name) {
        this.type = (byte) type;
        this.name = name;
    }
    public byte getType() {
        return type;
    }
    public String getName() {
        return name;
    }
    public static MapEncounterType getByType(int type){
        return allTypeArray[type];
    }
    public static MapEncounterType getByName(String name){
        for(MapEncounterType type : allTypeArray){
            if(type.name.equals(name)){
                return type;
            }
        }
        return null;
    }
}
