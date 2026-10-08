package org.pokemmo.gameserver.game.character;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.BattleRequestManager;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadPlayerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemoveEntityPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetFollowPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerTransportationPacket;
import org.server.Packet;
import org.server.Session;

/** Directional visibility of player entities only, not account presence, chat or NPCs. */
@Slf4j
public final class PlayerVisibilityService {
    private PlayerVisibilityService() {
    }

    public static boolean isHidden(CharacterManager subject) {
        PlayerEntity player = player(subject);
        return player != null && subject.getPlayerVisibility().isHidden(player.getEntityGameId());
    }

    /** Callers still select the appropriate map audience; this checks channel and visibility permissions. */
    public static boolean canSee(CharacterManager subject, CharacterManager viewer) {
        PlayerEntity source = player(subject);
        PlayerEntity observer = player(viewer);
        return source != null && observer != null && source.getEntityGameId() > 0 && observer.getEntityGameId() > 0
                && subject.getCharacterData().getChannel() == viewer.getCharacterData().getChannel()
                && allowed(isHidden(subject), observer.getPermission(), source.getEntityGameId() == observer.getEntityGameId());
    }

    static boolean allowed(boolean hidden, PermissionType viewerPermission, boolean self) {
        return self || !hidden || isAdministrator(viewerPermission);
    }

    public static boolean mutuallyVisible(CharacterManager first, CharacterManager second) {
        return canSee(first, second) && canSee(second, first);
    }

    /** The visibility check and send share the same monitor as toggle; no late reveal after hide. */
    public static void sendIfVisible(CharacterManager subject, Session recipient, Packet... packets) {
        if (subject == null) return;
        synchronized (subject.getPlayerVisibility()) {
            CharacterManager viewer = currentManager(recipient);
            if (currentManager(subject.getCharacterSession()) == subject && canSee(subject, viewer)) {
                recipient.send(packets);
            }
        }
    }

    public static void sendPlayer(CharacterManager subject, Session recipient) {
        if (subject == null) return;
        synchronized (subject.getPlayerVisibility()) {
            CharacterManager viewer = currentManager(recipient);
            PlayerEntity player = player(subject);
            if (currentManager(subject.getCharacterSession()) != subject || !canSee(subject, viewer)) return;
            recipient.send(new SendLoadPlayerPacket(subject.getCharacterData()),
                    new SendUpdatePlayerTransportationPacket(player.getEntityGameId(), player.getTransportation()));
        }
    }

    /** Removes a previous representation, including follower, but never the local player's entity. */
    public static void removePlayer(CharacterManager subject, Session recipient) {
        if (subject == null) return;
        synchronized (subject.getPlayerVisibility()) {
            CharacterManager viewer = currentManager(recipient);
            PlayerEntity source = player(subject);
            PlayerEntity observer = player(viewer);
            if (source == null || observer == null || source.getEntityGameId() <= 0
                    || source.getEntityGameId() == observer.getEntityGameId()
                    || subject.getCharacterData().getChannel() != viewer.getCharacterData().getChannel()) return;
            recipient.send(new SendSetFollowPokemonPacket(source.getEntityGameId(), 0, 0, true),
                    new SendRemoveEntityPacket(source.getEntityGameId()));
        }
    }

    public static void broadcast(CharacterManager subject, Packet packet, boolean includeSelf) {
        if (player(subject) == null) return;
        Set<Session> recipients = new LinkedHashSet<>();
        if (includeSelf) recipients.add(subject.getCharacterSession());
        for (MapData map : subject.getCurrentMapDatas()) {
            if (map != null) recipients.addAll(map.getPlayerSessionPool().values());
        }
        for (Session recipient : recipients) {
            if (includeSelf || recipient != subject.getCharacterSession()) sendIfVisible(subject, recipient, packet);
        }
    }

    public static boolean toggle(Session session, CharacterManager subject) {
        if (subject == null) throw new IllegalArgumentException("角色尚未就绪");
        boolean hidden;
        synchronized (subject.getInteractManager()) {
            PlayerEntity source = player(subject);
            if (currentManager(session) != subject || source == null || !isAdministrator(source.getPermission())) {
                throw new IllegalArgumentException("只有当前有效会话的 GM 及以上管理员可以切换隐身");
            }
            if (subject.getBattleManager() != null || TradeManager.isInTrade(subject)
                    || subject.getInteractManager().getInteractType() != InteractType.NONE) {
                throw new IllegalArgumentException("战斗、交易或场景交互期间不能切换隐身，请先结束当前交互");
            }
            CompletableFuture<Boolean> loading = subject.getMapLoadFuture();
            MapData map = subject.getCurrentMapDatas()[0];
            if (map == null || !map.equals(source.getRegionIndexId(), source.getMapHeaderIdOrGbaMapGroupId(), source.getGbaMapId())
                    || (loading != null && (!loading.isDone() || loading.isCompletedExceptionally()
                    || !Boolean.TRUE.equals(loading.getNow(false))))) {
                throw new IllegalArgumentException("地图尚未加载完成，不能切换隐身");
            }
            if (subject.getScriptManager() == null) throw new IllegalArgumentException("地图可见性上下文尚未就绪");
            synchronized (subject.getPlayerVisibility()) {
                hidden = subject.getPlayerVisibility().toggle(source.getEntityGameId());
                for (Session recipient : observingSessions(subject, map)) {
                    CharacterManager viewer = currentManager(recipient);
                    if (viewer == subject) continue;
                    if (canSee(subject, viewer)) sendPlayer(subject, recipient);
                    else removePlayer(subject, recipient);
                }
            }
        }
        // Request cancellation may acquire another character's state; do not hold our state monitor here.
        if (hidden) {
            BattleRequestManager.cancelFor(subject);
            TradeManager.cancelPendingFor(subject);
        }
        log.info("管理员隐身状态已切换: 角色编号={}, 隐身={}, 可见权限=GM及以上",
                subject.getCharacterData().getPlayerEntity().getEntityGameId(), hidden);
        return hidden;
    }

    private static Set<Session> observingSessions(CharacterManager subject, MapData sourceMap) {
        Set<Session> result = new LinkedHashSet<>();
        // Toggle is rare: inspect reverse map visibility too, including one-way connections.
        for (RegionData region : subject.getScriptManager().getRegionDatas()) {
            if (region == null) continue;
            for (MapData map : region.getRegionMaps().values()) {
                for (Session candidate : map.getPlayerSessionPool().values()) {
                    CharacterManager viewer = currentManager(candidate);
                    if (viewer == null || viewer.getCharacterData().getChannel() != subject.getCharacterData().getChannel()) continue;
                    for (MapData visible : viewer.getCurrentMapDatas()) {
                        if (visible != null && visible.equals(sourceMap)) {
                            result.add(candidate);
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean isAdministrator(PermissionType permission) {
        return permission != null && permission.getType() >= PermissionType.GM.getType();
    }

    private static CharacterManager currentManager(Session session) {
        if (session == null || !session.isActive()) return null;
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        return manager != null && manager.getCharacterSession() == session && player(manager) != null ? manager : null;
    }

    private static PlayerEntity player(CharacterManager manager) {
        return manager == null || manager.getCharacterData() == null ? null : manager.getCharacterData().getPlayerEntity();
    }
}
