package org.pokemmo.gameserver.game.item;

public enum ItemContainerType {
    VOID(0),
    INVENTORY(1),
    WAREHOUSE(2),
    MAIL(3),
    TEMPORARY_EVENT_INVENTORY(4),
    TEMPORARY_SHARED_EVENT_INVENTORY(5);
    private byte type;
    private static ItemContainerType[] allTypeArray = values();
    ItemContainerType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static ItemContainerType getByType(int type) {
        return allTypeArray[type];
    }
}
