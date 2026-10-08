package org.pokemmo.gameserver.protocol.packets.s2c;
import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.game.battle.BattleFormType;
import org.pokemmo.gameserver.game.battle.BattlePokemonData;
import org.pokemmo.gameserver.game.battle.BattleStatsBroadcastMode;
import org.pokemmo.gameserver.game.battle.FactionData;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.util.List;
@RequiredArgsConstructor
public class SendBattlePreviewPokemonDebutPacket extends OutgoingPacket {
    private final byte factionAmount;
    private final List<FactionData> factionDatas;
    private final boolean isReLoadBattleStatsBroadcastMode;
    private final BattleStatsBroadcastMode battleStatsBroadcastMode;
    private final byte playerFactionIndex;
    private final byte playerTeamIndex;
    private final boolean[] isShowPokemonAbilityValue;
    private final BattleFormType battleLevelType;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        //buffer.writeByte(factionAmount);
        for(int i = 0;i<factionAmount;i++){
            FactionData factionData = factionDatas.get(i);
            byte factionTeamSize = factionData.getFactionTeamSize();
            buffer.writeByte(factionTeamSize);
            for(int j = 0;j<factionTeamSize;j++) {
                byte teamPokemonSize = (byte) factionData.getFactionTeamByTeamIndex(j).getTeamPokemons().size();
                buffer.writeByte(teamPokemonSize);
                for(BattlePokemonData battlePokemonData : factionData.getFactionTeamByTeamIndex(j).getTeamPokemons()){
                    //battlePokemonData.getPokemonData().setPokemonIndexId((short) 50);
                    boolean isSelfFaction = factionData.getFactionIndex() == playerFactionIndex;
                    BattleTeamPokemonCodec battleTeamPokemonCodec = new BattleTeamPokemonCodec(isReLoadBattleStatsBroadcastMode, battleStatsBroadcastMode, playerTeamIndex, isSelfFaction, isShowPokemonAbilityValue);
                    battleTeamPokemonCodec.encode(buffer, battlePokemonData);
                }
            }
            for(int j = 0;j<1;j++){
                BattleDebutPokemonCodec battleDebutPokemonCodec = new BattleDebutPokemonCodec(playerTeamIndex, battleLevelType);
                //对于初始化预览宝可梦，需要发送队伍所有的宝可梦，不只是改登场的
                for(BattlePokemonData battlePokemonData : factionData.getFactionTeamByTeamIndex(0).getTeamPokemons()) {
                    //battlePokemonData.getPokemonData().setPokemonIndexId((short) 50);
                    battleDebutPokemonCodec.encode(buffer, battlePokemonData);
                }
            }
        }
    }
}
