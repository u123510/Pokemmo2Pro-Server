package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class RequsetChatChannelPacket extends IncomingPacket {
    private byte language;
    private short visableChatType;
    private short ignoreLanguages;

    @Override
    public void decode(ByteBufEx buffer) {
        language = buffer.readByte();
        visableChatType = buffer.readShortLE();
        ignoreLanguages = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
