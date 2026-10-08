package org.pokemmo.gameserver.game.entity;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapLoadingOptions;
import org.pokemmo.gameserver.game.npc.CustomNpcCatalog;
import org.pokemmo.gameserver.game.npc.CustomNpcDefinition;
import org.pokemmo.gameserver.game.region.RegionType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;

/** Persists custom NPC definitions before publishing them to the map and clients. */
@Singleton
@Slf4j
public final class NpcSpawnService {
    static final int MAX_MAP_NPCS = NpcSpawnRequest.MAX_MAP_NPCS;

    public record Spawned(int entityIdx, NpcEntity entity, Path file) {
    }

    public Spawned spawn(Session session, CharacterManager manager, NpcSpawnRequest request) {
        return spawn(session, manager, request, NpcSpawnAppearance.DEFAULT);
    }

    public Spawned spawn(Session session, CharacterManager manager, NpcSpawnRequest request,
                         NpcSpawnAppearance appearance) {
        if (manager == null) throw new IllegalArgumentException("角色尚未就绪");
        synchronized (manager.getInteractManager()) {
            MapData map = readyMap(session, manager);
            CustomNpcCatalog catalog = manager.getScriptManager().getCustomNpcCatalog();
            if (catalog == null) throw new IllegalArgumentException("自定义 NPC 存储未初始化");
            PlayerEntity player = manager.getCharacterData().getPlayerEntity();
            Set<Session> viewers = NpcVisibilityService.viewers(manager, map);
            Spawned result;
            synchronized (map) {
                int x = frontX(player.getX(), player.getToward());
                int y = frontY(player.getY(), player.getToward());
                for (Session viewer : viewers) {
                    CharacterManager other = viewer.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
                    if (other == null || other.getCharacterData() == null) continue;
                    PlayerEntity occupant = other.getCharacterData().getPlayerEntity();
                    if (occupant != null && map.equals(occupant.getRegionIndexId(),
                            occupant.getMapHeaderIdOrGbaMapGroupId(), occupant.getGbaMapId())
                            && occupant.getX() == x && occupant.getY() == y
                            && sameLayer(map, player.getZ(), occupant.getZ())) {
                        throw new IllegalArgumentException("前方格已有玩家，请更换位置");
                    }
                }
                int entityIdx = catalog.nextEntityIdx(map);
                result = createNpc(map, request, player.getX(), player.getY(), player.getZ(),
                        player.getToward(), manager.getSnowflakeIdGenerator()::nextId, entityIdx,
                        npc -> {
                            appearance.applyTo(npc);
                            return catalog.save(map, entityIdx, npc, appearance);
                        });
            }
            for (Session viewer : viewers) {
                NpcVisibilityService.sendSpawn(viewer, map, result.entity());
            }
            log.info("自定义 NPC 已保存并生成: 操作者={}, 地图={}, NPC序号={}, NPC编号={}, 外观地区={}, 外观编号={}, 位置=({},{},{}), 移动类型={}, 活动范围=({},{}), 事件分类={}, 闪光={}, 缩放={}, 文件={}",
                    player.getEntityGameId(), map.getMapKey(), result.entityIdx(), result.entity().getEntityGameId(),
                    request.spriteRegion(), request.spriteId(), result.entity().getX(), result.entity().getY(),
                    result.entity().getZ(), request.movementType(), request.leashX(), request.leashY(),
                    appearance.eventId(), appearance.sparkles(), appearance.spriteScale(), result.file());
            return result;
        }
    }

    // Called under the map monitor; exposed within the package for in-memory regression tests.
    static Spawned createNpc(MapData map, NpcSpawnRequest request, int playerX, int playerY,
                             int z, int toward, LongSupplier ids, int entityIdx, Function<NpcEntity, Path> persist) {
        synchronized (map) {
            request.validateForMap(Byte.toUnsignedInt(map.getRegionIndexId()));
            if (toward < 0 || toward > 3 || z < Byte.MIN_VALUE || z > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("角色朝向或高度无效");
            }
            int x = frontX(playerX, toward);
            int y = frontY(playerY, toward);
            if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE
                    || x >= map.getMapWidth() || y >= map.getMapHeight() || !map.checkIsWalkable(x, y)) {
                throw new IllegalArgumentException("前方格超出地图范围或不可站立，请面对地图内的空地");
            }
            if (map.getNpcEntityHashMap().size() >= MAX_MAP_NPCS) {
                throw new IllegalArgumentException("当前地图 NPC 数量已达到 1024 上限");
            }
            for (NpcEntity npc : map.getNpcEntityHashMap().values()) {
                if (npc.isLoad() && npc.getX() == x && npc.getY() == y && sameLayer(map, z, npc.getZ())) {
                    throw new IllegalArgumentException("前方格已有 NPC，请更换位置");
                }
            }
            if (entityIdx < CustomNpcDefinition.FIRST_ENTITY_IDX
                    || map.getNpcEntityHashMap().containsKey("npc_" + entityIdx)) {
                throw new IllegalArgumentException("自定义 NPC 序号无效或已占用，不能覆盖原生 NPC");
            }
            long id = ids.getAsLong();
            if (id <= 0 || map.containsNpcEntity(id)) {
                throw new IllegalStateException("生成的 NPC 编号无效或重复");
            }
            NpcEntity npc = new NpcEntity(id, true, "npc_" + entityIdx, toward ^ 1,
                    request.movementType(), request.leashX(), request.leashY(),
                    map.getRegionIndexId(), map.getMapHeaderIdOrGBAmapGroupId(), map.getGbaMapId(),
                    request.spriteRegion(), request.spriteId(), x, y, z);
            Path file = persist.apply(npc);
            if (file == null) throw new IllegalStateException("自定义 NPC 保存未返回文件，未发布实体");
            map.addEntity(npc);
            return new Spawned(entityIdx, npc, file);
        }
    }

    private static MapData readyMap(Session session, CharacterManager manager) {
        if (session == null || !session.isActive() || manager.getCharacterSession() != session
                || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager
                || manager.getCharacterData() == null || manager.getCharacterData().getPlayerEntity() == null) {
            throw new IllegalArgumentException("角色或连接尚未就绪");
        }
        if (!MapLoadingOptions.areNpcsEnabled()) {
            throw new IllegalArgumentException("NPC 加载开关已关闭，不能生成 NPC");
        }
        if (manager.getBattleManager() != null || TradeManager.isInTrade(manager)
                || manager.getInteractManager().getInteractType() != InteractType.NONE) {
            throw new IllegalArgumentException("战斗、交易或交互期间不能生成 NPC，请先关闭当前交互");
        }
        CompletableFuture<Boolean> loading = manager.getMapLoadFuture();
        if (loading != null && (!loading.isDone() || loading.isCompletedExceptionally()
                || !Boolean.TRUE.equals(loading.getNow(false)))) {
            throw new IllegalArgumentException("地图尚未加载完成，请稍后再试");
        }
        MapData[] maps = manager.getCurrentMapDatas();
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        if (maps == null || maps.length == 0 || maps[0] == null || !maps[0].equals(
                player.getRegionIndexId(), player.getMapHeaderIdOrGbaMapGroupId(), player.getGbaMapId())) {
            throw new IllegalArgumentException("当前地图不可用或与角色位置不一致");
        }
        return maps[0];
    }

    private static int frontX(int x, int toward) {
        return x + (toward == 2 ? -1 : toward == 3 ? 1 : 0);
    }

    private static int frontY(int y, int toward) {
        return y + (toward == 0 ? 1 : toward == 1 ? -1 : 0);
    }

    private static boolean sameLayer(MapData map, int first, int second) {
        return RegionType.isGBA(map.getRegionIndexId())
                ? first < 0 || second < 0 || first / 3 == second / 3 : first == second;
    }
}
