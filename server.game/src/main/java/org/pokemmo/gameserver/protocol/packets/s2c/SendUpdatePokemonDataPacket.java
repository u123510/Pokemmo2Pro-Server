package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.UpdatePokemonDataCodec;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.pokemon.UpdatePokemonData;

@RequiredArgsConstructor
public class SendUpdatePokemonDataPacket extends OutgoingPacket {
    private final UpdatePokemonData updatePokemonData;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        UpdatePokemonDataCodec REFRESH_POKEMON_CODEC = new UpdatePokemonDataCodec(updatePokemonData);
        REFRESH_POKEMON_CODEC.encode(buffer,updatePokemonData.getUpdatePokemon());
    }
}
