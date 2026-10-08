package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public final class SendTradePokemonPacket extends OutgoingPacket {
    private final short slot;
    private final PokemonData pokemon;

    @Override
    public void encode(ByteBufEx buffer) {
        if (pokemon == null) {
            throw new IllegalArgumentException("客户端没有独立的交易宝可梦清空封包");
        }
        if (slot < 0 || slot >= PokemonContainerType.TRADE.getSize()) {
            throw new IllegalArgumentException("交易宝可梦槽位越界: " + slot);
        }
        // S2C 0x52 is a full PokemonCodec record; it has no side field.
        Codecs.POKEMON_CODEC.encode(buffer, pokemon, PokemonContainerType.TRADE.getType(), slot);
    }
}
