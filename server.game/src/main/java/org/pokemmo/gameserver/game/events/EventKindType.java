package org.pokemmo.gameserver.game.events;

public enum EventKindType {
    BADGE(0),
    CITY_FLY(1),
    STORY_LINE(2),
    RUNNING_SHOE(3),
    FAME(4);
    private byte type;
    EventKindType(int type){
        this.type = (byte) type;
    }
     public byte getType(){
        return type;
    }
}
