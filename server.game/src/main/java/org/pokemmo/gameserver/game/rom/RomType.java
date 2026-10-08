package org.pokemmo.gameserver.game.rom;

public enum RomType {
    GBA(0),
    NDS(1);
    private byte type;
    private static final RomType[] allTypeArray = {GBA, NDS};
    RomType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static RomType getByType(int type) {
        return allTypeArray[type];
    }
}
