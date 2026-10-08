package org.pokemmo.gameserver.game.giftshop;

import lombok.Getter;

/**
 * 礼品商城分类枚举，对应客户端 f.E10
 */
@Getter
public enum GiftShopCategory {
    SPECIAL((byte) 0, "特殊"),
    DONATOR((byte) 1, "捐赠者状态"),
    VANITY((byte) 2, "时装"),
    CONSUMABLES((byte) 3, "消耗品"),
    MISC((byte) 4, "杂货"),
    FURNITURE((byte) 5, "装饰品"),
    OCARINAS((byte) 6, "技能陶笛"),
    PARTICLES((byte) 7, "质子"),
    VOUCHERS((byte) 8, "点券");

    private final byte id;
    private final String description;

    GiftShopCategory(byte id, String description) {
        this.id = id;
        this.description = description;
    }

    public static GiftShopCategory fromId(byte id) {
        for (GiftShopCategory category : values()) {
            if (category.id == id) {
                return category;
            }
        }
        return MISC;
    }
}
