package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.gameserver.command.CommandDispatcher;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.redis.RedisUtil;
import org.server.union.chat.ChatMessage;
import org.server.union.chat.ChatType;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.server.union.language.LanguageType;

public class ChatPacket extends IncomingPacket {
    @Inject
    private RedisUtil redisUtil;
    @Inject
    private CommandDispatcher commandDispatcher;
    private ChatType chatType;
    private String message;
    @Override
    public void decode(ByteBufEx buffer) {
        chatType = ChatType.getByType(buffer.readByte());
        message = buffer.readUtf16LE();
    }
    @Override
    public void handle(Session session) throws Exception {
        if (chatType == ChatType.NORMAL && commandDispatcher.dispatch(session, message)) {
            return;
        }
        long senderId = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData().getPlayerEntity().getEntityGameId();
        byte senderPermission = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData().getPlayerEntity().getPermission().getType();
        String sender = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData().getPlayerEntity().getEntityName();
        ChatMessage chatMessage = new ChatMessage(senderId, senderPermission, sender, chatType, LanguageType.ENGLISH, message);
        RedisUtil.pushToQueue(chatMessage);
    }
}
