package org.pokemmo.gameserver.game.container;

public enum PokemonContainerType {

    PC(0, 660, true,"pc"),
    PARTY(1, 6, true,"party"),
    TRADE(2, 6, false,"trade"),
    DAYCARE(3, 26, false,"daycare"),
    RENTAL_PARTY(4, 6, false,"rental_party"),
    MAIL(5, 0, false,"mail"),
    AUCTION(6, 0, false,"auction"),
    VOID(7, 0, false,"void"),
    DELETED(8, 0, false,"deleted"),
    EVENT(9, 6, false,"event");
    private byte type;
    private short size;
    private boolean isRequire;
    private String name;
    private static final PokemonContainerType[] allTypeArray = values();
    PokemonContainerType(int type, int size, boolean isRequire, String name) {
        this.type = (byte) type;
        this.size = (short) size;
        this.isRequire = isRequire;
        this.name = name;
    }
    public short getSize() {
        return size;
    }
    public byte getType() {
        return type;
    }
    public String getName() {
        return name;
    }
    public boolean isRequire() {
        return isRequire;
    }
    public static PokemonContainerType getByType(int type){
        return allTypeArray[type];
    }
}
