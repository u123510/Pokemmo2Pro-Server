package org.pokemmo.gameserver.game.shop;

/** A resource NPC, not a runtime Object ID: map file stem plus entityIdx. */
public record ShopNpcBinding(String map, int entityIdx) {
    public ShopNpcBinding {
        if (map == null || map.isBlank() || !map.equals(map.trim()) || map.length() > 128
                || map.contains("/") || map.contains("\\") || map.endsWith(".json")) {
            throw new IllegalArgumentException("npcs.map 必须是地图文件名（不含目录和 .json），长度 1..128");
        }
        if (entityIdx < 0) {
            throw new IllegalArgumentException("npcs.entityIdx 必须是非负整数，不是运行时 NPC 编号");
        }
    }

    String npcName() {
        return "npc_" + entityIdx;
    }
}
