package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.character.CharacterData;

public class CharacterGuildCodec implements ObjectCodec<CharacterData> {
    @Override
    public CharacterData decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, CharacterData object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, CharacterData object) {
        boolean inGuild = false;
        buffer.writeBoolean(inGuild);
        if (!inGuild) {
            return;
        }
        buffer.writeUtf16LE(""); // unused
        buffer.writeIntLE(0);
    }
}
