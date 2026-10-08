package org.pokemmo.gameserver.game.item;

public class ItemInfo {
    private int itemIndexId;
    private String name;
    private String desc;
    private byte regionIndexId;
    private int nameLocalStringIndexId;
    private int descLocalStringIndexId;
    public ItemInfo(int itemIndexId, String name, String desc, byte regionIndexId, int nameLocalStringIndexId, int descLocalStringIndexId) {
        this.itemIndexId = itemIndexId;
        this.name = name;
        this.desc = desc;
        this.regionIndexId = regionIndexId;
        this.nameLocalStringIndexId = nameLocalStringIndexId;
        this.descLocalStringIndexId = descLocalStringIndexId;
    }
}
