package org.pokemmo.gameserver.game.item;

public enum ItemType {
    MAIN(1),
    BALLS(2),
    MOVE_LEARNER_TOOLS(3),
    BERRIES(4),
    KEY_ITEMS(5),
    COSMETICS(6),
    MEDICINE(7),
    BATTLE_ITEMS(8);
    private byte type;
    private static ItemType[] allTypeArray = values();
    ItemType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static ItemType getByType(int type) {
        for(ItemType itemType : allTypeArray) {
            if(itemType.type == (byte) type) {
                return itemType;
            }
        }
        return null;
    }
}
