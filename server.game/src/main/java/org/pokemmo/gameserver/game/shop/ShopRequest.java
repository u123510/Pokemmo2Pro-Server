package org.pokemmo.gameserver.game.shop;

/** Requests never carry prices; targetId is an item index for BUY and an owned-item ID for SELL. */
public record ShopRequest(int action, long quoteId, int requestId, long targetId, int amount) {
    public static final int BUY = 1;
    public static final int SELL = 2;
    public static final int MAX_AMOUNT = 999;

    public ShopRequest {
        if ((action != BUY && action != SELL) || quoteId <= 0 || requestId <= 0
                || targetId <= 0 || (action == BUY && targetId > 0xFFFF)
                || amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("商店请求类型、报价编号、请求序号、目标或数量无效");
        }
    }
}
