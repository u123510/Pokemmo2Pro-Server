package org.pokemmo.gameserver.protocol.packets.s2c;
import org.pokemmo.gameserver.game.battle.BattlePokemonData;
import org.pokemmo.gameserver.game.battle.effect.BasePokemonActionEffect;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.BattleRoundResultCodec;

import java.util.List;

public class SendBattlePokemonActionWithSelectorPacket extends OutgoingPacket {
    private long attackerPokemonId;
    private short usedMoveIndexId;
    private byte contestIndexData;
    private List<BattlePokemonData> sufferPokemons;
    private BattleRoundResultCodec battleRoundResultCodec = new BattleRoundResultCodec();
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(attackerPokemonId);
        buffer.writeShortLE(usedMoveIndexId);
        buffer.writeByte(contestIndexData);
        buffer.writeByte(sufferPokemons.size());
        for(BattlePokemonData sufferPokemon : sufferPokemons){
            buffer.writeLongLE(sufferPokemon.getPokemonData().getPokemonId());
            buffer.writeShortLE(sufferPokemon.getSufferFlag());
            buffer.writeByte(sufferPokemon.getSufferActions().size());
            for(BasePokemonActionEffect sufferAction : sufferPokemon.getSufferActions()){
                battleRoundResultCodec.encode(buffer,sufferAction);
            }
        }
    }
    public SendBattlePokemonActionWithSelectorPacket(long attackerPokemonId, int usedMoveIndexId, int contestIndexData, List<BattlePokemonData> sufferPokemons) {
        this.attackerPokemonId = attackerPokemonId;
        this.usedMoveIndexId = (short) usedMoveIndexId;
        this.contestIndexData = (byte) contestIndexData;
        this.sufferPokemons = sufferPokemons;
    }
}
