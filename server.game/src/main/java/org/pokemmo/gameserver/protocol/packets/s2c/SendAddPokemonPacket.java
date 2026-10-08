package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;

public class SendAddPokemonPacket extends OutgoingPacket {
    private final PokemonData pokemon;

    /** When set, the packet projects the record into a packet-local container slot. */
    private final PokemonContainerType projectedContainer;
    private final short projectedPosition;

    public SendAddPokemonPacket(PokemonData pokemon) {
        this(pokemon, null, (short) 0);
    }

    public SendAddPokemonPacket(PokemonData pokemon,
                                 PokemonContainerType projectedContainer,
                                 short projectedPosition) {
        this.pokemon = pokemon;
        this.projectedContainer = projectedContainer;
        this.projectedPosition = projectedPosition;
    }

    @Override
    public void encode(ByteBufEx buffer) {
        if (pokemon == null) {
            throw new IllegalArgumentException("宝可梦不能为空");
        }
        if (projectedContainer == null) {
            Codecs.POKEMON_CODEC.encode(buffer, pokemon);
            return;
        }
        if (projectedPosition < 0 || projectedPosition >= projectedContainer.getSize()) {
            throw new IllegalArgumentException("宝可梦投影槽位越界: container="
                    + projectedContainer + ", position=" + projectedPosition);
        }
        Codecs.POKEMON_CODEC.encode(buffer, pokemon,
                projectedContainer.getType(), projectedPosition);
    }
}
