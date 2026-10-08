package org.pokemmo.gameserver.codecs;

import org.pokemmo.gameserver.game.battle.BattlePokemonData;
import org.server.bytes.ByteBufEx;

public class BattlePreviewPokemonCodec implements ObjectCodec<BattlePokemonData> {
    @Override
    public BattlePokemonData decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(ByteBufEx buffer, BattlePokemonData object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, BattlePokemonData object) {
        buffer.writeByte(object.isHasDebutPokemon() ? (byte) 1 : (byte) 0);
        if (!object.isHasDebutPokemon()) {
            return;
        }
        //buffer.writeLongLE(object.getPokemonData().getPokemonId());
        buffer.writeShortLE(object.getPokemonData().getPokemonIndexId());
        buffer.writeByte(object.getPokemonData().getLevel());
        buffer.writeByte(object.getPokemonData().getPokemonSex());
        buffer.writeByte(object.getPokemonData().getFormType());
        buffer.writeShortLE(object.getPokemonBattleItem());
    }
}
