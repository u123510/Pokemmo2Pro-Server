package org.pokemmo.gameserver.game.item;

public enum ItemEffectType {
    NOT_USABLE(0),
    DEFAULT(1),
    MONSTER(2),
    SKILL(3),
    TEACH_SKILL(4),
    MONSTER_ACTIVE_ONLY(5),
    MONSTER_BATTLE_SLOT_ONLY(6),
    UNK(7);
    private byte type;
    private static ItemEffectType[] allTypeArray = values();
    ItemEffectType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static ItemEffectType getByType(int type) {
        for(ItemEffectType itemEffectType : allTypeArray){
            if(itemEffectType.type == (byte) type){
                return itemEffectType;
            }
        }
        return null;
    }
}
