package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailListPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameMailPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.mail.MailService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** C2S 0x97: deletes one mail after all of its attachments are claimed. */
@Slf4j
public final class DeleteEmailPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES + Short.BYTES;

    private long mailId;
    private short pageIndex;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "邮件删除请求长度错误: " + buffer.readableBytes());
        }
        mailId = buffer.readLongLE();
        pageIndex = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("收到邮件删除请求，但当前 Session 没有已加载角色");
            return;
        }
        if (mailId <= 0 || pageIndex < 0) {
            log.warn("忽略非法邮件删除请求: mailId={}, pageIndex={}", mailId, pageIndex);
            return;
        }

        long characterId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        MailService.MailDeleteResult result = gameServerService.deleteMail(characterId, mailId);
        if (!result.success() && !result.blockedByAttachments()) {
            log.debug("邮件删除请求未更新记录: characterId={}, mailId={}",
                    characterId, mailId);
            return;
        }

        MailService.MailCounts counts = gameServerService.getMailCounts(characterId);
        MailService.MailListPage page = gameServerService.getMailList(
                characterId, result.sentMailbox(), pageIndex);
        session.send(
                new SendGameMailPacket(counts.received(), counts.unread(), counts.sent()),
                new SendEmailListPacket(pageIndex, result.sentMailbox(), page.entries()));
    }
}
