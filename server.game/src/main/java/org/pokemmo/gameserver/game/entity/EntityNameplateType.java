package org.pokemmo.gameserver.game.entity;

public enum EntityNameplateType {
    NONE(0),
    WILD(1),
    PVP(2),
    SPECTATE(3),
    WANTS_REMATCH(4),
    NO_REMATCH(5),
    TRAINER_AGGRO(6),
    DISCONNECTED(7);
    private byte type;
    private static final EntityNameplateType[] allTypeArray = {NONE,WILD,PVP,SPECTATE,WANTS_REMATCH,NO_REMATCH,TRAINER_AGGRO,DISCONNECTED};
    EntityNameplateType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
     public static EntityNameplateType getByType(int type){
        return allTypeArray[type];
    }
}
