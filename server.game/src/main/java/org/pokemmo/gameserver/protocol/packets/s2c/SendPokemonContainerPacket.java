package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SendPokemonContainerPacket extends OutgoingPacket {
    private final ContainerRecord container;
    private final List<PokemonData> pokemons;
    private final boolean isContainerExist = true;
    private final boolean isContainerEmpty = false;

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(container.getId());
        byte flags = 0;
        if (isContainerExist) flags |= 1;
        if (isContainerEmpty) flags |= 2;
        buffer.writeByte(flags);
        buffer.writeByte(pokemons.size());
        for (PokemonData pokemon : pokemons) {
            Codecs.POKEMON_CODEC.encode(buffer, pokemon);
        }
    }
}
