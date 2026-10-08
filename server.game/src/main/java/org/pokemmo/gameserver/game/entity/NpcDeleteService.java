package org.pokemmo.gameserver.game.entity;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.npc.CustomNpcCatalog;
import org.pokemmo.gameserver.game.shop.ShopCatalog;
import org.pokemmo.gameserver.game.shop.ShopSessions;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemoveEntityPacket;
import org.server.Session;

/** Deletes only catalog-owned custom NPCs, persisting a tombstone before any live removal. */
@Singleton
@Slf4j
public final class NpcDeleteService {
    public record Deleted(MapData map, long npcId, int entityIdx, Path file) {
    }

    public Deleted delete(Session session, CharacterManager manager, long npcId) {
        if (npcId <= 0) throw new IllegalArgumentException("NPC编号必须是正的运行时 Object ID");
        if (manager == null || manager.getScriptManager() == null) throw new IllegalArgumentException("角色尚未就绪");
        CustomNpcCatalog custom = manager.getScriptManager().getCustomNpcCatalog();
        if (custom == null) throw new IllegalArgumentException("自定义 NPC 存储未初始化");
        Supplier<Deleted> mutation = () -> {
            synchronized (manager.getInteractManager()) {
                MapData map = readyMap(session, manager);
                return remove(map, npcId, npc -> custom.disable(map, npc));
            }
        };
        // Lock order matches ShopService: catalog -> actor -> map -> custom catalog.
        ShopCatalog shops = manager.getScriptManager().getShopCatalog();
        Deleted deleted = shops == null ? mutation.get() : shops.withNpcMutation(mutation);
        int closed = ShopSessions.closeForNpc(npcId);
        Set<Session> viewers = NpcVisibilityService.viewers(manager, deleted.map());
        for (Session viewer : viewers) {
            if (NpcVisibilityService.canSee(viewer, deleted.map())) {
                viewer.send(new SendRemoveEntityPacket(npcId));
            }
        }
        log.info("自定义 NPC 已删除并永久停用: 操作者={}, 地图={}, NPC序号={}, NPC编号={}, 已关闭商店={}, 文件={}",
                manager.getCharacterData().getPlayerEntity().getEntityGameId(), deleted.map().getMapKey(),
                deleted.entityIdx(), npcId, closed, deleted.file());
        return deleted;
    }

    static Deleted remove(MapData map, long npcId, Function<NpcEntity, CustomNpcCatalog.Disabled> persist) {
        synchronized (map) {
            NpcEntity npc = map.getNpcEntityByGameId(npcId);
            if (npcId <= 0 || npc == null) {
                throw new IllegalArgumentException("当前地图没有该 NPC，可能已删除或编号已失效；不能使用固定序号代替 Object ID");
            }
            CustomNpcCatalog.Disabled saved = persist.apply(npc);
            if (saved == null || saved.file() == null) throw new IllegalStateException("停用保存未确认，未删除在线 NPC");
            npc.setLoad(false);
            npc.setCanInteract(false);
            if (!map.removeEntity(npc)) throw new IllegalStateException("NPC 已停用保存，但在线目标发生变化，请核对地图");
            return new Deleted(map, npcId, saved.entityIdx(), saved.file());
        }
    }

    private static MapData readyMap(Session session, CharacterManager manager) {
        if (session == null || !session.isActive() || manager.getCharacterSession() != session
                || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager
                || manager.getCharacterData() == null || manager.getCharacterData().getPlayerEntity() == null) {
            throw new IllegalArgumentException("角色或连接尚未就绪");
        }
        if (manager.getBattleManager() != null || TradeManager.isInTrade(manager)
                || manager.getInteractManager().getInteractType() != InteractType.NONE) {
            throw new IllegalArgumentException("战斗、交易或交互期间不能删除 NPC，请先关闭当前交互");
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
}
