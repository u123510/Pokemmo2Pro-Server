package org.pokemmo.gameserver.game.npc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.NpcSpawnAppearance;
import org.pokemmo.gameserver.game.entity.NpcSpawnRequest;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapLoadingOptions;
import org.pokemmo.gameserver.game.region.RegionType;

/** Restores durable custom NPCs after base maps and before shop bindings are loaded. */
@Slf4j
public final class CustomNpcCatalog {
    private final CustomNpcStore store;
    private final Map<String, MapData> maps = new HashMap<>();
    private final Map<String, CustomNpcDefinition> definitions = new HashMap<>();
    private final Map<NpcEntity, CustomNpcDefinition> liveDefinitions = new IdentityHashMap<>();
    private boolean ready;

    public record Disabled(int entityIdx, Path file) {
    }

    public CustomNpcCatalog(Path directory, List<MapData> loadedMaps) {
        store = new CustomNpcStore(directory);
        try {
            for (MapData map : loadedMaps) {
                String key = mapKey(map);
                if (maps.putIfAbsent(key, map) != null) throw new IllegalArgumentException("自定义 NPC 地图名称重复: " + key);
            }
            List<CustomNpcStore.Located> files = store.readAll();
            Map<MapData, List<NpcEntity>> additions = new HashMap<>();
            for (CustomNpcStore.Located file : files) {
                CustomNpcDefinition definition = file.definition();
                try {
                    MapData map = requireMap(definition);
                    if (definitions.putIfAbsent(definition.key(), definition) != null
                            || map.getNpcEntityHashMap().containsKey(definition.npcName())) {
                        throw new IllegalArgumentException("自定义 NPC 序号重复或与原地图冲突");
                    }
                    if (!definition.enabled()) continue;
                    List<NpcEntity> staged = additions.computeIfAbsent(map, key -> new ArrayList<>());
                    validatePlacement(map, definition, staged);
                    NpcEntity entity = definition.toEntity(map);
                    staged.add(entity);
                    liveDefinitions.put(entity, definition);
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(file.file() + ": " + exception.getMessage(), exception);
                }
            }
            if (MapLoadingOptions.areNpcsEnabled()) {
                additions.forEach((map, npcs) -> npcs.forEach(map::addEntity));
            }
            ready = true;
            log.info("自定义 NPC 配置加载完成: 目录={}, 配置数量={}, 启用数量={}, NPC加载开关={}",
                    directory.toAbsolutePath().normalize(), files.size(),
                    additions.values().stream().mapToInt(List::size).sum(), MapLoadingOptions.areNpcsEnabled());
        } catch (IOException | RuntimeException exception) {
            // A bad file must not let a subsequent Spawn overwrite or reuse its reserved identity.
            definitions.clear();
            liveDefinitions.clear();
            log.error("自定义 NPC 加载失败，未发布自定义实体并已禁止新增；原地图 NPC 不变: {}", exception.getMessage(), exception);
        }
    }

    public synchronized int nextEntityIdx(MapData map) {
        requireReady(map);
        long next = CustomNpcDefinition.FIRST_ENTITY_IDX;
        for (CustomNpcDefinition definition : definitions.values()) {
            if (definition.mapKey().equals(mapKey(map))) next = Math.max(next, (long) definition.entityIdx() + 1);
        }
        while (next <= Integer.MAX_VALUE && map.getNpcEntityHashMap().containsKey("npc_" + next)) next++;
        if (next > Integer.MAX_VALUE) throw new IllegalArgumentException("自定义 NPC 序号已用尽");
        return (int) next;
    }

    public synchronized Path save(MapData map, int entityIdx, NpcEntity npc) {
        return save(map, entityIdx, npc, NpcSpawnAppearance.DEFAULT);
    }

    public synchronized Path save(MapData map, int entityIdx, NpcEntity npc, NpcSpawnAppearance appearance) {
        requireReady(map);
        if (definitions.size() >= 10000) throw new IllegalArgumentException("自定义 NPC 文件数量已达到 10000 上限");
        CustomNpcDefinition definition = CustomNpcDefinition.from(map, entityIdx, npc, appearance);
        if (definitions.containsKey(definition.key()) || store.exists(definition)
                || map.getNpcEntityHashMap().containsKey(definition.npcName())) {
            throw new IllegalArgumentException("自定义 NPC 序号或文件已存在，禁止覆盖；请重启重新读取配置");
        }
        validatePlacement(map, definition, List.of());
        try {
            Path path = store.saveNew(definition);
            definitions.put(definition.key(), definition);
            liveDefinitions.put(npc, definition);
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("自定义 NPC 保存失败: " + exception.getMessage(), exception);
        }
    }

    /** Caller holds the map monitor; only an entity actually loaded/saved here may be disabled. */
    public synchronized Disabled disable(MapData map, NpcEntity npc) {
        requireReady(map);
        CustomNpcDefinition definition = liveDefinitions.get(npc);
        if (definition == null || !definition.enabled() || !definition.mapKey().equals(mapKey(map))
                || map.getNpcEntityHashMap().get(definition.npcName()) != npc) {
            throw new IllegalArgumentException("目标不是当前地图中已保存的自定义 NPC，禁止删除原生或未保存 NPC");
        }
        try {
            Path path = store.disable(definition);
            definitions.put(definition.key(), definition.disabled());
            liveDefinitions.remove(npc);
            return new Disabled(definition.entityIdx(), path);
        } catch (IOException exception) {
            throw new UncheckedIOException("自定义 NPC 停用保存失败: " + exception.getMessage(), exception);
        }
    }

    private void requireReady(MapData map) {
        if (!ready || maps.get(mapKey(map)) != map) {
            throw new IllegalArgumentException("自定义 NPC 存储未就绪或地图不匹配，请检查启动日志");
        }
    }

    private MapData requireMap(CustomNpcDefinition definition) {
        MapData map = maps.get(definition.mapKey());
        if (map == null) throw new IllegalArgumentException("自定义 NPC 地图不存在或未加载: " + definition.mapKey());
        return map;
    }

    private static void validatePlacement(MapData map, CustomNpcDefinition definition, List<NpcEntity> staged) {
        if (definition.x() >= map.getMapWidth() || definition.y() >= map.getMapHeight()
                || !map.checkIsWalkable(definition.x(), definition.y())) {
            throw new IllegalArgumentException("自定义 NPC 出生格越界或不可站立");
        }
        if (map.getNpcEntityHashMap().size() + staged.size() >= NpcSpawnRequest.MAX_MAP_NPCS) {
            throw new IllegalArgumentException("地图 NPC 总数超过 1024");
        }
        for (NpcEntity npc : map.getNpcEntityHashMap().values()) checkCollision(map, definition, npc);
        for (NpcEntity npc : staged) checkCollision(map, definition, npc);
    }

    private static void checkCollision(MapData map, CustomNpcDefinition definition, NpcEntity npc) {
        boolean sameLayer = RegionType.isGBA(map.getRegionIndexId())
                ? definition.z() < 0 || npc.getZ() < 0 || definition.z() / 3 == npc.getZ() / 3
                : definition.z() == npc.getZ();
        if (npc.isLoad() && sameLayer && npc.getX() == definition.x() && npc.getY() == definition.y()) {
            throw new IllegalArgumentException("自定义 NPC 出生格与其他 NPC 重叠");
        }
    }

    private static String mapKey(MapData map) {
        return Byte.toUnsignedInt(map.getRegionIndexId()) + "/" + map.getMapKey();
    }
}
