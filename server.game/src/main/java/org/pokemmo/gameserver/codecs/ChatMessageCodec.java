package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.server.union.chat.ChatMessage;
import org.server.union.chat.ChatType;


public class ChatMessageCodec implements ObjectCodec<ChatMessage> {
    private long playerId =0;
    @Override
    public ChatMessage decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, ChatMessage object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, ChatMessage message) {
        ChatType chatType = message.getChatType();
        buffer.writeByte(chatType.getType());
        if (chatType == ChatType.LINK || chatType == ChatType.SYSTEM_ANNOUNCEMENTS) {
            buffer.writeUtf16LE(message.getMessage());
        } else {
            buffer.writeLongLE(playerId);
            buffer.writeUtf16LE(message.getSender());
            buffer.writeByte(message.getLanguageType().getType());
            buffer.writeByte(-1);//senderLevel
            buffer.writeUtf16LE(message.getMessage());
        }

    }
}
