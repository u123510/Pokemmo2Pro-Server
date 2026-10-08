package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** C2S 0x62: request a trade with a player on the current map. */
public class RequestTradePacket extends IncomingPacket {
    private String targetPlayerName;

    @Override
    public void decode(ByteBufEx buffer) {
        targetPlayerName = buffer.readUtf16LE();
    }

    @Override
    public void handle(Session session) {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager != null) {
            characterManager.handleTradeRequest(targetPlayerName);
        }
    }
}
