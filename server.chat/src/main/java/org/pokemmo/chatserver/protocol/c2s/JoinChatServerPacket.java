package org.pokemmo.chatserver.protocol.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class JoinChatServerPacket extends IncomingPacket {
    private byte playerCountry;
    private short visableChatTypes;
    private short ignoreLanguages;
    private byte currentChannel;

    @Override
    public void decode(ByteBufEx buffer) {
        playerCountry = buffer.readByte();
        visableChatTypes = buffer.readShortLE();
        ignoreLanguages = buffer.readShortLE();
        currentChannel = buffer.readByte();
    }
    @Override
    public void handle(Session session) {

    }
}
