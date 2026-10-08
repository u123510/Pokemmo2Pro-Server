package org.pokemmo.gameserver.game.shop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapData;

/** Validates resource targets and resolves bindings without mutating live NPCs. */
public final class ShopNpcBindings {
    private record Key(byte region, byte bank, byte map, String npcName) {
        static Key of(MapData map, String npcName) {
            return new Key(map.getRegionIndexId(), map.getMapHeaderIdOrGBAmapGroupId(),
                    map.getGbaMapId(), npcName);
        }
    }

    private final Map<Key, String> shopByNpc;

    private ShopNpcBindings(Map<Key, String> shopByNpc) {
        this.shopByNpc = Map.copyOf(shopByNpc);
    }

    // Only unpublished candidates are changed; all conflict participants are rejected.
    static ShopNpcBindings compile(Map<String, ShopDefinition> shops, List<MapData> maps,
                                   Map<String, Path> origins, List<String> errors) {
        Map<String, List<MapData>> mapsByName = new HashMap<>();
        for (MapData map : maps) {
            if (map.getMapKey() != null) {
                mapsByName.computeIfAbsent(map.getMapKey(), key -> new ArrayList<>()).add(map);
            }
        }
        Map<Key, String> owners = new HashMap<>();
        Set<String> rejected = new HashSet<>();
        for (ShopDefinition shop : shops.values()) {
            if (shop.npcs() == null) continue;
            for (ShopNpcBinding target : shop.npcs()) {
                List<MapData> matches = mapsByName.getOrDefault(target.map(), List.of());
                if (matches.size() != 1) {
                    rejected.add(shop.shopId());
                    errors.add(origins.get(shop.shopId()) + ": NPC 绑定地图未加载或名称不唯一: " + target.map());
                    continue;
                }
                MapData map = matches.get(0);
                if (!map.getNpcEntityHashMap().containsKey(target.npcName())) {
                    rejected.add(shop.shopId());
                    errors.add(origins.get(shop.shopId()) + ": 地图 " + target.map()
                            + " 中不存在 NPC 序号 " + target.entityIdx() + "，请核对 entityIdx 和 NPC 加载开关");
                    continue;
                }
                String previous = owners.putIfAbsent(Key.of(map, target.npcName()), shop.shopId());
                if (previous != null) {
                    rejected.add(previous);
                    rejected.add(shop.shopId());
                    errors.add(origins.get(shop.shopId()) + ": NPC 绑定冲突: 地图=" + target.map()
                            + ", NPC序号=" + target.entityIdx() + ", 店铺=" + previous + "/" + shop.shopId()
                            + ", 原文件=" + origins.get(previous));
                }
            }
        }
        rejected.forEach(shops::remove);
        owners.entrySet().removeIf(entry -> rejected.contains(entry.getValue()));
        return new ShopNpcBindings(owners);
    }

    public String resolveShopId(MapData map, NpcEntity npc, Map<String, ShopDefinition> shops) {
        if (map == null || npc == null || !map.equals(npc.getRegionIndexId(),
                npc.getMapHeaderIdOrGbaMapGroupId(), npc.getGbaMapId())) {
            return null;
        }
        String explicit = shopByNpc.get(Key.of(map, npc.getNpcName()));
        if (explicit != null) return explicit;
        String legacy = npc.getShopId();
        if (legacy == null || legacy.isBlank()) return null;
        ShopDefinition shop = shops.get(legacy);
        // An explicit list (including []) replaces this shop's old map-side bindings.
        return shop != null && shop.npcs() != null ? null : legacy;
    }
}
