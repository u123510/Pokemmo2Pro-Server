package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;

import lombok.RequiredArgsConstructor;
import org.server.union.chat.ChatMessage;

@RequiredArgsConstructor
public class SendChatMessagePacket extends OutgoingPacket {
    private final ChatMessage chatMessage;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        Codecs.CHAT_MESSAGE_CODEC.encode(buffer, chatMessage);
    }
}
