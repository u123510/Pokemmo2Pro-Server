package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendAcknowledgeCharacterSelectionPacket extends OutgoingPacket {
    private final CharacterData character;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        boolean isAuthorized = character != null;
        buffer.writeBoolean(isAuthorized);
        if (!isAuthorized)
            return;
        Codecs.CHARACTER_CODEC_NO_MAC.encode(buffer, character);
    }
}
