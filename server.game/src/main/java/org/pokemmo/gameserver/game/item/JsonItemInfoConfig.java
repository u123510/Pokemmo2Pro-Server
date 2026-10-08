package org.pokemmo.gameserver.game.item;

public class JsonItemInfoConfig {
    private int itemIndexId;
    private String name;
    private String desc;
    private byte regionIndexId;
    private int nameLocalStringIndexId;
    private int descLocalStringIndexId;
    public int getItemIndexId() {
        return itemIndexId;
    }
    public ItemInfo toItemInfo() {
        return new ItemInfo(itemIndexId, name, desc, regionIndexId, nameLocalStringIndexId, descLocalStringIndexId);
    }
}
