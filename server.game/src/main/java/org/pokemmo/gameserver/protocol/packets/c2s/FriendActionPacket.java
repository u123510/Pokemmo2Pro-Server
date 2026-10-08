package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.friend.FriendManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendFriendListPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInteractPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** C2S 0x60: add or remove a player from the client's friend list. */
@Slf4j
public final class FriendActionPacket extends IncomingPacket {
    private static final int MAX_FIELD_LENGTH = 32;

    private String targetPlayerName;
    private String auxiliaryValue;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        targetPlayerName = readUtf16LeField(buffer, "目标玩家名称");
        auxiliaryValue = readUtf16LeField(buffer, "好友附加字段");
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("好友操作请求包含未消费的尾部数据");
        }

        if (targetPlayerName.isBlank()) {
            throw new IllegalArgumentException("好友目标名称不能为空");
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            return;
        }

        long characterId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        long targetId = gameServerService.findFriendCharacterIdByName(targetPlayerName);
        if (targetId <= 0 || targetId == characterId) {
            log.warn("好友目标不存在或不能添加自己: playerId={}, target={}, auxiliary={}",
                    characterId, targetPlayerName, auxiliaryValue);
            return;
        }

        // Existing entries are removed immediately. A new entry is only
        // persisted after the target accepts the online request dialog.
        if (gameServerService.isFriend(characterId, targetId)) {
            if (gameServerService.removeFriend(characterId, targetId)) {
                log.info("好友关系已移除: playerId={}, friendId={}, target={}, auxiliary={}",
                        characterId, targetId, targetPlayerName, auxiliaryValue);
            } else {
                log.warn("删除好友关系失败: playerId={}, friendId={}, target={}, auxiliary={}",
                        characterId, targetId, targetPlayerName, auxiliaryValue);
            }
            session.send(new SendFriendListPacket(gameServerService.getFriendList(characterId)));
            return;
        }

        Session targetSession = GameSessionPool.getPlayerSessionInPool(targetId);
        CharacterManager targetManager = targetSession == null
                ? null
                : targetSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (targetManager == null || targetManager.getCharacterData() == null
                || targetManager.getCharacterData().getPlayerEntity() == null
                || !targetSession.isActive()
                || targetManager.getCharacterData().getPlayerEntity().getEntityGameId() != targetId) {
            log.warn("好友请求目标不在线: playerId={}, targetId={}, target={}",
                    characterId, targetId, targetPlayerName);
            return;
        }

        FriendManager.PendingFriendRequest request = FriendManager.registerRequest(
                characterManager, targetManager);
        if (request == null) {
            log.warn("好友请求被拒绝或目标正忙: playerId={}, targetId={}, target={}, auxiliary={}",
                    characterId, targetId, targetPlayerName, auxiliaryValue);
            return;
        }
        targetSession.send(new SendInteractPacket(
                -1L,
                targetManager.getInteractManager().getInteractTimes(),
                request.script()));
        log.info("已发送好友请求 UI: requesterId={}, targetId={}, target={}, auxiliary={}",
                characterId, targetId, targetPlayerName, auxiliaryValue);
    }

    private static String readUtf16LeField(ByteBufEx buffer, String fieldName) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index <= MAX_FIELD_LENGTH; index++) {
            if (buffer.readableBytes() < Character.BYTES) {
                throw new IllegalArgumentException(fieldName + "缺少 UTF-16LE 终止符");
            }
            char character = buffer.readCharLE();
            if (character == '\0') {
                return value.toString();
            }
            value.append(character);
        }
        throw new IllegalArgumentException(fieldName + "长度超过 " + MAX_FIELD_LENGTH + " 个字符");
    }
}
