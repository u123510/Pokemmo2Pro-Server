package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendCharacterPokemonAbilityPacket extends OutgoingPacket {
    private short characterPokemonAbility;
    public SendCharacterPokemonAbilityPacket(int characterPokemonAbility) {
        this.characterPokemonAbility = (short)characterPokemonAbility;
    }
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(characterPokemonAbility);
    }
}
