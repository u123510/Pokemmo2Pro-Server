package org.pokemmo.gameserver.game.shop;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ShopDefinition(String shopId, boolean buyEnabled, boolean sellEnabled,
                             List<ShopItem> items, List<ShopNpcBinding> npcs) {
    public static final int MAX_ITEMS = 1024;
    public static final int MAX_NPCS = 1024;

    public ShopDefinition(String shopId, boolean buyEnabled, boolean sellEnabled, List<ShopItem> items) {
        this(shopId, buyEnabled, sellEnabled, items, null);
    }

    public ShopDefinition {
        if (shopId == null || !shopId.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("shopId 必须是 1..128 位小写字母、数字、点、下划线或连字符");
        }
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("每家店铺必须配置 1..1024 件商品");
        }
        Set<Integer> seen = new HashSet<>();
        for (ShopItem item : items) {
            if (item == null || !seen.add(item.itemId())) {
                throw new IllegalArgumentException("店铺存在空商品或重复 itemId");
            }
        }
        items = List.copyOf(items);
        // null preserves legacy map bindings; [] explicitly unbinds this shop.
        if (npcs != null) {
            if (npcs.size() > MAX_NPCS || npcs.stream().anyMatch(binding -> binding == null)
                    || new HashSet<>(npcs).size() != npcs.size()) {
                throw new IllegalArgumentException("npcs 不能超过 1024 项，不能包含空条目或重复 NPC");
            }
            npcs = List.copyOf(npcs);
        }
    }

    public ShopItem item(int itemId) {
        return items.stream().filter(item -> item.itemId() == itemId).findFirst().orElse(null);
    }

    public List<ShopItem> buyItems() {
        return buyEnabled ? items.stream().filter(item -> item.buyPrice() != null).toList() : List.of();
    }

    public List<ShopItem> sellItems() {
        return sellEnabled ? items.stream().filter(item -> item.sellPrice() != null).toList() : List.of();
    }
}
