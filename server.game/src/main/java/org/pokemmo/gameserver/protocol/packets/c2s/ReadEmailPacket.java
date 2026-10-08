package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEmailDetailPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameMailPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.mail.MailService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** C2S 0x96: opens one mail and marks it read when the player is the recipient. */
@Slf4j
public final class ReadEmailPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES;

    private long mailId;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "邮件详情请求长度错误: " + buffer.readableBytes());
        }
        mailId = buffer.readLongLE();
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("收到邮件详情请求，但当前 Session 没有已加载角色");
            session.send(new SendEmailDetailPacket(null));
            return;
        }
        if (mailId <= 0) {
            log.warn("忽略非法邮件详情请求: mailId={}", mailId);
            session.send(new SendEmailDetailPacket(null));
            return;
        }

        long characterId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        MailService.MailDetail detail = gameServerService.getMailDetail(characterId, mailId);
        if (detail == null) {
            log.debug("邮件详情不可用或无权访问: characterId={}, mailId={}",
                    characterId, mailId);
        }

        MailService.MailCounts counts = gameServerService.getMailCounts(characterId);
        session.send(new SendEmailDetailPacket(detail),
                new SendGameMailPacket(counts.received(), counts.unread(), counts.sent()));
    }
}
