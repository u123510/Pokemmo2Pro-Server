package org.pokemmo.gameserver.game.giftshop;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼品商城 JSON 配置根对象
 */
@Data
public class JsonGiftShopConfig {
    private List<GiftShopItem> items = new ArrayList<>();
}
