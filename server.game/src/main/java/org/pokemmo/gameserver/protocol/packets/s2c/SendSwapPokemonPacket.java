package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.battle.BattlePokemonData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;

@RequiredArgsConstructor
public class SendSwapPokemonPacket extends OutgoingPacket {
    private final byte pokemonSelectData;
    private final boolean isSelfFaction;
    private final BattlePokemonData pokemonData;
    private final BattleTeamPokemonCodec battleFactionPokemonCodec;
    private final BattleDebutPokemonCodec battleDebutPokemonCodec;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(pokemonSelectData);
        buffer.writeBoolean(isSelfFaction);
        if(isSelfFaction) {
            battleFactionPokemonCodec.encode(buffer, pokemonData);
        }
        battleDebutPokemonCodec.encode(buffer, pokemonData);
    }
}
