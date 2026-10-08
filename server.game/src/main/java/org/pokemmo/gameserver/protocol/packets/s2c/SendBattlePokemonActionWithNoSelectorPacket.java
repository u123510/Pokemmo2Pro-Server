package org.pokemmo.gameserver.protocol.packets.s2c;
import org.pokemmo.gameserver.codecs.BattleRoundResultCodec;
import org.pokemmo.gameserver.game.battle.BattlePokemonCauseAction;
import org.pokemmo.gameserver.game.battle.effect.BasePokemonActionEffect;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
public class SendBattlePokemonActionWithNoSelectorPacket extends OutgoingPacket {
    private  long actorPokemonId;
    private  long targetPokemonId;
    private  BasePokemonActionEffect pokemonActionEffect;
    private  BattleRoundResultCodec battleRoundResultCodec = new BattleRoundResultCodec();
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
          buffer.writeLongLE(actorPokemonId);
          buffer.writeLongLE(targetPokemonId);
          battleRoundResultCodec.encode(buffer,pokemonActionEffect);
    }
    public SendBattlePokemonActionWithNoSelectorPacket(long actorPokemonId, long targetPokemonId, BasePokemonActionEffect pokemonActionEffects) {
        this.actorPokemonId = actorPokemonId;
        this.targetPokemonId = targetPokemonId;
        this.pokemonActionEffect = pokemonActionEffects;
    }
    public SendBattlePokemonActionWithNoSelectorPacket(BattlePokemonCauseAction battlePokemonCauseAction) {
        this.actorPokemonId = battlePokemonCauseAction.getActorPokemonId();
        this.targetPokemonId = battlePokemonCauseAction.getTargetPokemonId();
        this.pokemonActionEffect = battlePokemonCauseAction.getCauseActionEffect();
    }
}
