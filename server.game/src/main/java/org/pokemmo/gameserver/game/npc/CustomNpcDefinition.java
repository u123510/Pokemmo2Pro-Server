package org.pokemmo.gameserver.game.npc;

import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.NpcSpawnAppearance;
import org.pokemmo.gameserver.game.entity.NpcSpawnRequest;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.region.RegionType;

/** Durable spawn definition. Runtime Object IDs are deliberately not persisted. */
public record CustomNpcDefinition(int version, boolean enabled, String map, int regionId, int entityIdx,
                                  int spriteId, int spriteRegion, int movementType, int leashX, int leashY,
                                  int x, int y, int z, int toward, int eventId, boolean sparkles, float spriteScale) {
    public static final int FIRST_ENTITY_IDX = 100000;

    public CustomNpcDefinition(int version, boolean enabled, String map, int regionId, int entityIdx,
                               int spriteId, int spriteRegion, int movementType, int leashX, int leashY,
                               int x, int y, int z, int toward) {
        this(version, enabled, map, regionId, entityIdx, spriteId, spriteRegion, movementType, leashX, leashY,
                x, y, z, toward, -1, false, 1.0f);
    }

    public CustomNpcDefinition {
        if (version != 1 && version != 2) throw new IllegalArgumentException("自定义 NPC 配置版本必须为 1 或 2");
        NpcSpawnAppearance appearance = new NpcSpawnAppearance(eventId, sparkles, spriteScale);
        if (version == 1 && !appearance.equals(NpcSpawnAppearance.DEFAULT)) {
            throw new IllegalArgumentException("事件分类、闪光或缩放需要自定义 NPC 配置版本 2");
        }
        if (map == null || !map.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("自定义 NPC 地图名必须是安全的地图文件名，不含目录或扩展名");
        }
        if (entityIdx < FIRST_ENTITY_IDX) {
            throw new IllegalArgumentException("自定义 NPC 序号必须从 100000 开始，不与原生 NPC 序号混用");
        }
        new NpcSpawnRequest(spriteId, 0, spriteRegion, movementType, leashX, leashY).validateForMap(regionId);
        if (x < 0 || x > Short.MAX_VALUE || y < 0 || y > Short.MAX_VALUE
                || z < Byte.MIN_VALUE || z > Byte.MAX_VALUE || toward < 0 || toward > 3) {
            throw new IllegalArgumentException("自定义 NPC 的坐标、高度或朝向无效");
        }
    }

    public String npcName() {
        return "npc_" + entityIdx;
    }

    public CustomNpcDefinition disabled() {
        return new CustomNpcDefinition(version, false, map, regionId, entityIdx, spriteId, spriteRegion,
                movementType, leashX, leashY, x, y, z, toward, eventId, sparkles, spriteScale);
    }

    public String mapKey() {
        return regionId + "/" + map;
    }

    public String key() {
        return mapKey() + "/" + entityIdx;
    }

    public String regionName() {
        return RegionType.getByType(regionId).getName();
    }

    public static CustomNpcDefinition from(MapData map, int entityIdx, NpcEntity npc) {
        return from(map, entityIdx, npc, NpcSpawnAppearance.DEFAULT);
    }

    public static CustomNpcDefinition from(MapData map, int entityIdx, NpcEntity npc, NpcSpawnAppearance appearance) {
        if (!npc.getNpcName().equals("npc_" + entityIdx) || !map.equals(npc.getRegionIndexId(),
                npc.getMapHeaderIdOrGbaMapGroupId(), npc.getGbaMapId())) {
            throw new IllegalArgumentException("自定义 NPC 与保存目标地图或序号不一致");
        }
        float scale = npc.isSpriteScaleOverride() ? npc.getSpriteScaleOverride() : 1.0f;
        if (npc.isUnk6() != appearance.sparkles() || Float.compare(scale, appearance.spriteScale()) != 0) {
            throw new IllegalArgumentException("在线 NPC 闪光或缩放与保存配置不一致");
        }
        int version = appearance.equals(NpcSpawnAppearance.DEFAULT) ? 1 : 2;
        return new CustomNpcDefinition(version, true, map.getMapKey(), Byte.toUnsignedInt(map.getRegionIndexId()),
                entityIdx, Short.toUnsignedInt(npc.getNpcModelIndexId()), Byte.toUnsignedInt(npc.getNpcModelRegionIndexId()),
                Byte.toUnsignedInt(npc.getMoveMentType()), npc.getMovementLeashX(), npc.getMovementLeashY(),
                npc.getX(), npc.getY(), npc.getZ(), npc.getDefaultToward(),
                appearance.eventId(), appearance.sparkles(), appearance.spriteScale());
    }

    public NpcEntity toEntity(MapData target) {
        if (!map.equals(target.getMapKey()) || regionId != Byte.toUnsignedInt(target.getRegionIndexId())) {
            throw new IllegalArgumentException("自定义 NPC 恢复到错误地图");
        }
        NpcEntity npc = new NpcEntity(0, enabled, npcName(), toward, movementType, leashX, leashY,
                target.getRegionIndexId(), target.getMapHeaderIdOrGBAmapGroupId(), target.getGbaMapId(),
                spriteRegion, spriteId, x, y, z);
        new NpcSpawnAppearance(eventId, sparkles, spriteScale).applyTo(npc);
        return npc;
    }
}
