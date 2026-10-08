package org.pokemmo.gameserver.game.shop;

/** Prices are from the player's perspective; null disables that direction. */
public record ShopItem(int itemId, Integer buyPrice, Integer sellPrice) {
    public ShopItem {
        if (itemId <= 0 || itemId > 0xFFFF) {
            throw new IllegalArgumentException("itemId 必须在 1..65535 范围内");
        }
        if ((buyPrice != null && buyPrice <= 0) || (sellPrice != null && sellPrice <= 0)) {
            throw new IllegalArgumentException("商品价格必须为正整数，禁用方向请填写 null");
        }
        if (buyPrice == null && sellPrice == null) {
            throw new IllegalArgumentException("商品至少需要一种买卖价格");
        }
    }
}
