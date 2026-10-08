package org.pokemmo.gameserver.game.interact;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.map.KantoMetaTileBehaviorType;
import org.pokemmo.gameserver.game.map.KantoregionMapData;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.story.StoryService;
import org.pokemmo.gameserver.game.story.PalletStoryNpcs;
import org.pokemmo.gameserver.game.shop.ShopService;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;

/** Resolves scene A-button targets; an empty request does not identify a PC. */
@Slf4j
public final class SceneInteractionService {
    private final PcInteractionService pcInteractionService;
    private final ShopService shopService;

    @Inject
    public SceneInteractionService(PcInteractionService pcInteractionService,
                                   ShopService shopService) {
        this.pcInteractionService = pcInteractionService;
        this.shopService = shopService;
    }

    public void handleEmptyInteraction(Session session) {
        CharacterManager manager = readyCharacter(session);
        if (manager == null) {
            return;
        }
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        MapData map = manager.getCurrentMapDatas()[0];
        int dx;
        int dy;
        switch (player.getToward()) {
            case 0 -> { dx = 0; dy = 1; }
            case 1 -> { dx = 0; dy = -1; }
            case 2 -> { dx = -1; dy = 0; }
            case 3 -> { dx = 1; dy = 0; }
            default -> {
                log.debug("场景交互被拒绝: 朝向无效, {}", describe(player));
                return;
            }
        }
        int targetX = player.getX() + dx;
        int targetY = player.getY() + dy;
        NpcEntity npc = findNpc(map, player, targetX, targetY);
        if (npc == null && map instanceof KantoregionMapData gbaMap
                && gbaMap.getMetatileBehavior(targetX, targetY)
                == KantoMetaTileBehaviorType.MB_COUNTER) {
            // Never infer a counter merely from an unwalkable tile.
            npc = findNpc(map, player, targetX + dx, targetY + dy);
        }
        if (npc != null) {
            startNpcInteraction(session, manager, npc);
            return;
        }
        if (PcInteractionService.isPcTarget(player)) {
            log.debug("场景交互目标已识别: 目标=电脑, {}", describe(player));
            pcInteractionService.openMenu(session, manager);
            return;
        }
        boolean background = map.getBgEvents().values().stream()
                .anyMatch(event -> event.getX() == targetX && event.getY() == targetY
                        && event.getZ() == player.getZ());
        log.debug("场景交互未处理: 目标={}, 前方坐标=({},{}), {}",
                background ? "背景事件" : "未匹配", targetX, targetY, describe(player));
    }

    /** Explicit 0x22 targets still use the client's entity ID, not a guessed position. */
    public void handleNpcInteraction(Session session, long npcGameId) {
        CharacterManager manager = readyCharacter(session);
        if (manager == null) {
            return;
        }
        NpcEntity npc = manager.getCurrentMapDatas()[0].getNpcEntityByGameId(npcGameId);
        if (npc == null || npc.getEntityGameId() <= 0 || !npc.isCanInteract()
                || PalletStoryNpcs.project(manager, manager.getCurrentMapDatas()[0], npc) == null) {
            log.debug("NPC 交互被拒绝: 目标不可用, NPC编号={}", npcGameId);
            return;
        }
        NpcEntity view = PalletStoryNpcs.project(manager, manager.getCurrentMapDatas()[0], npc);
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        int distance = Math.abs(view.getX() - player.getX()) + Math.abs(view.getY() - player.getY());
        if (distance > 2 || !sameLayer(manager.getCurrentMapDatas()[0], player, view)) return;
        startNpcInteraction(session, manager, npc);
    }

    private static CharacterManager readyCharacter(Session session) {
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null) {
            log.debug("场景交互被拒绝: 角色尚未就绪");
            return null;
        }
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        MapData[] maps = manager.getCurrentMapDatas();
        if (maps == null || maps.length == 0 || maps[0] == null
                || !maps[0].equals(player.getRegionIndexId(),
                player.getMapHeaderIdOrGbaMapGroupId(), player.getGbaMapId())) {
            log.debug("场景交互被拒绝: 当前地图尚未就绪, {}", describe(player));
            return null;
        }
        if (manager.getInteractManager().getInteractType() != InteractType.NONE
                || manager.getBattleManager() != null || TradeManager.isInTrade(manager)) {
            log.debug("场景交互被拒绝: 角色正忙, 交互类型={}, 战斗中={}, 交易中={}, {}",
                    manager.getInteractManager().getInteractType(),
                    manager.getBattleManager() != null, TradeManager.isInTrade(manager), describe(player));
            return null;
        }
        return manager;
    }

    private static NpcEntity findNpc(MapData map, PlayerEntity player, int x, int y) {
        Session session = map.getPlayerSession(player.getEntityGameId());
        CharacterManager manager = session == null ? null : session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        for (NpcEntity npc : map.getNpcEntityHashMap().values()) {
            NpcEntity view = PalletStoryNpcs.project(manager, map, npc);
            if (view != null && npc.getEntityGameId() > 0 && npc.isCanInteract()
                    && view.getX() == x && view.getY() == y
                    && sameLayer(map, player, view)) {
                return npc;
            }
        }
        return null;
    }

    private static boolean sameLayer(MapData map, PlayerEntity player, NpcEntity npc) {
        if (map instanceof KantoregionMapData) {
            // Client ga0_0 compares GBA layers in groups of three.
            return player.getZ() < 0 || npc.getZ() < 0
                    || player.getZ() / 3 == npc.getZ() / 3;
        }
        return player.getZ() == npc.getZ();
    }

    private void startNpcInteraction(Session session, CharacterManager manager, NpcEntity npc) {
        log.debug("场景交互目标已识别: 目标=NPC, NPC编号={}, NPC名称={}, 脚本={}, {}",
                npc.getEntityGameId(), npc.getNpcName(), npc.getInteractionScriptName(),
                describe(manager.getCharacterData().getPlayerEntity()));
        if (StoryService.beforeShop(manager, npc)) {
            return;
        }
        if (shopService.tryOpen(session, manager, npc)) {
            return;
        }
        if (!StoryService.onNpc(manager, npc)) {
            if (ItemInteractionService.tryCollect(manager, npc)) {
                return;
            }
            if (TrainerInteractionService.tryStart(manager, npc)) {
                return;
            }
            log.debug("NPC 交互未处理: 脚本尚未实现, NPC编号={}, NPC名称={}, 脚本={}",
                    npc.getEntityGameId(), npc.getNpcName(), npc.getInteractionScriptName());
        }
    }

    private static String describe(PlayerEntity player) {
        return String.format("位置=(%d,%d,%d,%d,%d,%d), 朝向=%d",
                Byte.toUnsignedInt(player.getRegionIndexId()),
                Byte.toUnsignedInt(player.getMapHeaderIdOrGbaMapGroupId()),
                Byte.toUnsignedInt(player.getGbaMapId()),
                player.getX(), player.getY(), player.getZ(), player.getToward());
    }
}
