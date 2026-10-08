package org.pokemmo.gameserver.game.battle;

public enum BattleTeamType {
    PLAYER(0, 0,1,true,"Player"),
    WILD (1, 1,2,false,"Wild"),
    TRAINER(2,2,3,false,"Trainer"),
    CUSTOM_TRAINER(3, 3,4,false,"Custom Trainer"),
    COOPERATIVE_PLAYERS(4,4,6,true,"Cooperative Players"),
    COOPERATIVE_NPC(5,5,5,false,"Cooperative NPC"),
    NULL (6,100,7,false,"Null");
    public static final BattleTeamType[] allTypeArray = {PLAYER,WILD,TRAINER,CUSTOM_TRAINER,COOPERATIVE_PLAYERS,COOPERATIVE_NPC,NULL};
    private byte type;
    private byte value;
    private byte mappingValue;
    private boolean isPlayerFaction;
    private String name;
    BattleTeamType(int type, int value, int mappingValue, boolean isPlayerFaction, String name){
        this.type = (byte) type;
        this.value = (byte) value;
        this.mappingValue = (byte) mappingValue;
        this.isPlayerFaction = isPlayerFaction;
        this.name = name;
    }
    public byte getType(){
        return this.type;
    }
    public static BattleTeamType getTeamType(int type){
        return allTypeArray[type];
    }
    public static BattleTeamType getByName(String name){
        for(BattleTeamType teamType : allTypeArray){
            if(teamType.name.equals(name)){
                return teamType;
            }
        }
        return null;
    }
    public String getName(){
        return this.name;
    }
    public byte getValue(){
        return this.value;
    }
    public byte getMappingValue(){
        return this.mappingValue;
    }
    public boolean equals(BattleTeamType teamType){
        return this.type == teamType.type;
    }
}
