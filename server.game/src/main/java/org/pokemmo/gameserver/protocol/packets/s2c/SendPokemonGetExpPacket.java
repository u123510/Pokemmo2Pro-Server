package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.pokemon.PokemonGetExpData;
@RequiredArgsConstructor
public class SendPokemonGetExpPacket extends OutgoingPacket {
    private final PokemonGetExpData pokemonGetExpInfo;
    private byte flag = 0;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(pokemonGetExpInfo.getPokemonId());
        buffer.writeIntLE(pokemonGetExpInfo.getBaseExp());
        if (pokemonGetExpInfo.isHasTrainerBattleBonus()) flag |= 1;
        if (pokemonGetExpInfo.isHasTradeBonus()) flag |= 2;
        if (pokemonGetExpInfo.isHasCharmExpBonus()) flag |= 4;
        if (pokemonGetExpInfo.isHasDonatorStatusBonus()) flag |= 8;
        if (pokemonGetExpInfo.isHasHeldItemBonus()) flag |= 16;
        if (pokemonGetExpInfo.isHasExpReamplifierBonus()) flag |= 32;
        buffer.writeByte(flag);
        if(pokemonGetExpInfo.isHasTrainerBattleBonus()){
            buffer.writeIntLE((int)(pokemonGetExpInfo.getBaseExp() * pokemonGetExpInfo.getTrainerBattleBonus()));
        }
        if(pokemonGetExpInfo.isHasTradeBonus()){
            buffer.writeIntLE((int)(pokemonGetExpInfo.getBaseExp() * pokemonGetExpInfo.getTradeBonus()));
        }
        if(pokemonGetExpInfo.isHasCharmExpBonus()){
            buffer.writeIntLE((int)(pokemonGetExpInfo.getBaseExp() * pokemonGetExpInfo.getCharmExpBonus()));
        }
        if(pokemonGetExpInfo.isHasDonatorStatusBonus()){
            buffer.writeIntLE((int)(pokemonGetExpInfo.getBaseExp() * pokemonGetExpInfo.getDonatorStatusBonus()));
        }
        if(pokemonGetExpInfo.isHasHeldItemBonus()){
            buffer.writeIntLE((int)(pokemonGetExpInfo.getBaseExp() * pokemonGetExpInfo.getHeldItemBonus()));
        }
        if(pokemonGetExpInfo.isHasExpReamplifierBonus()){
            buffer.writeIntLE((int)(pokemonGetExpInfo.getBaseExp() * pokemonGetExpInfo.getExpReamplifierBonus()));
        }
    }
}
