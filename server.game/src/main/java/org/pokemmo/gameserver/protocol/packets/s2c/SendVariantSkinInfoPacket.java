package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendVariantSkinInfoPacket extends OutgoingPacket {
    private final CharacterData characterData;
    private final boolean isVariant = true;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(characterData.getPlayerEntity().getEntityGameId());
        buffer.writeBoolean(isVariant);
        Codecs.SKIN_CODEC_1.encode(buffer, characterData);
        buffer.writeByte(characterData.getPlayerEntity().getSex());
    }
}
