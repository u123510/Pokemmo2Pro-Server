package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailListPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class RequestEmailPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Short.BYTES + 1;

    private short currentPageIndex;
    private boolean isSendMailType;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "邮箱列表请求长度错误: " + buffer.readableBytes());
        }
        currentPageIndex = buffer.readShortLE();
        int mailType = buffer.readUnsignedByte();
        if (mailType > 1) {
            throw new IllegalArgumentException("邮箱类型必须为 0 或 1: " + mailType);
        }
        isSendMailType = mailType == 1;
    }

    @Override
    public void handle(Session session) throws Exception {
        if (currentPageIndex < 0) {
            throw new IllegalArgumentException("邮箱页码不能为负数: " + currentPageIndex);
        }
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            // 邮箱窗口可能在角色上下文完成绑定前发起首个请求；返回合法空页，
            // 避免因异常关闭 Session 后客户端永久停留在加载状态。
            session.send(new SendEmailListPacket(currentPageIndex, isSendMailType));
            return;
        }
        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        var page = gameServerService.getMailList(characterId, isSendMailType, currentPageIndex);
        session.send(new SendEmailListPacket(currentPageIndex, isSendMailType, page.entries()));
    }
}
