package org.pokemmo.gameserver.game.battle;

public enum BattleStateType {
    STATE_STARTING(-2,false ),
    STATE_START_WAIT(-1,false ),
    STATE_STARTED(0,false ),
    STATE_PRETURN(1,false ),
    STATE_TURN_ACTION_SELECT(2,false ),
    STATE_TURN_RUN(3,false ),
    STATE_PENDING_POKEMON_SWAPS(4,false ),
    STATE_POKEMON_SWAPS(5,false ),
    STATE_ENDED(6,false ),
    STATE_TEAM_PREVIEW(7,true),
    STATE_TEAM_PREVIEW_INIT(8,false),
    STATE_TEAM_PREVIEW_SELF_READONLY(9,true),
    STATE_TEAM_PREVIEW_INPROGRESS(10,false),
    STATE_TEAM_PREVIEW_SELF_READONLY_INPROGRESS(11,false),
    STATE_TEAM_PREVIEW_FINISHED(12,false),
    STATE_SPAWN_DEN(13,false);
    private static BattleStateType[] allTypeArray = {STATE_STARTING,STATE_START_WAIT,STATE_STARTED,STATE_PRETURN,STATE_TURN_ACTION_SELECT,STATE_TURN_RUN,STATE_PENDING_POKEMON_SWAPS,STATE_POKEMON_SWAPS,STATE_ENDED,STATE_TEAM_PREVIEW,STATE_TEAM_PREVIEW_INIT,STATE_TEAM_PREVIEW_SELF_READONLY,STATE_TEAM_PREVIEW_INPROGRESS,STATE_TEAM_PREVIEW_SELF_READONLY_INPROGRESS,STATE_TEAM_PREVIEW_FINISHED,STATE_SPAWN_DEN};
    public static BattleStateType getBattleState(int type){
        for(BattleStateType battleState:allTypeArray){
            if(battleState.type == (byte)type){
                return battleState;
            }
        }
        return null;
    }
    public byte getType() {
        return type;
    }
    private boolean isPreview;
    private byte type;
    BattleStateType(int type, boolean isPreview){
        this.type = (byte) type;
        this.isPreview = isPreview;
    }
    public boolean getIsPreview() {
        return isPreview;
    }
    public boolean equals(BattleStateType battleState){
        return this.type == battleState.type;
    }
}
