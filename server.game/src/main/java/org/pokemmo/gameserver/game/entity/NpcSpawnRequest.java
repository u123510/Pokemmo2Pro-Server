package org.pokemmo.gameserver.game.entity;

import java.util.Set;

import org.pokemmo.gameserver.game.region.RegionType;

/** Field order follows client f.uk_0.vl0(), not map coordinates. */
public record NpcSpawnRequest(int spriteId, int scriptOffset, int spriteRegion,
                              int movementType, int leashX, int leashY) {
    public static final int MAX_MAP_NPCS = 1024;
    private static final Set<Integer> SPRITE_REGIONS = Set.of(0, 1, 2, 3, 4, 10);
    // Ordinary movement entries from the NPC Tool; event-specific custom AI is excluded.
    private static final Set<Integer> GBA_MOVEMENTS =
            Set.of(0, 1, 2, 3, 6, 8, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24);
    private static final Set<Integer> NDS_MOVEMENTS =
            Set.of(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);

    public NpcSpawnRequest {
        if (spriteId < 0 || spriteId > 10000) {
            throw new IllegalArgumentException("外观编号必须在 0..10000 范围内");
        }
        if (scriptOffset != 0) {
            throw new IllegalArgumentException("脚本偏移目前只支持 0；服务端尚未接入 ROM 数值偏移脚本");
        }
        if (!SPRITE_REGIONS.contains(spriteRegion)) {
            throw new IllegalArgumentException("外观地区必须是 0、1、2、3、4 或 10，不是当前地图编号");
        }
        if (movementType < 0 || movementType > 127) {
            throw new IllegalArgumentException("移动类型必须在 0..127 范围内");
        }
        if (leashX < 0 || leashX > 4 || leashY < 0 || leashY > 4) {
            throw new IllegalArgumentException("横向和纵向活动范围必须分别在 0..4 范围内");
        }
    }

    public void validateForMap(int regionId) {
        if (regionId != 0 && regionId != 1 && regionId != 3) {
            throw new IllegalArgumentException("当前地区尚不支持自定义 NPC 生成");
        }
        Set<Integer> movements = RegionType.isGBA(regionId) ? GBA_MOVEMENTS : NDS_MOVEMENTS;
        if (!movements.contains(movementType)) {
            throw new IllegalArgumentException("移动类型不适用于当前地图或属于未接入的特殊事件行为；静止请填 0");
        }
    }
}
