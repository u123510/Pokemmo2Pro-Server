package org.pokemmo.gameserver.codecs;

import org.pokemmo.gameserver.game.battle.*;
import org.pokemmo.gameserver.game.battle.effect.CooperativeTeam;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class BattleTeamInfoCodec implements ObjectCodec<BattleTeam> {
    private final boolean hasInterruption;
    @Override
    public BattleTeam decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(ByteBufEx buffer, BattleTeam object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, BattleTeam object) {
        BattleTeamType currentTeamType = object.getTeamType();
        buffer.writeByte(currentTeamType.getType());
        if(!currentTeamType.equals(BattleTeamType.NULL)){
            buffer.writeByte(object.getTeamMaxPokemonAmount());
        }
        switch (currentTeamType){
            case PLAYER:
                PlayerTeam playerTeam = (PlayerTeam) object;
                buffer.writeUtf16LE(playerTeam.getCharacterData().getPlayerEntity().getPlayerName());
                buffer.writeByte(playerTeam.getCharacterData().getPlayerEntity().getSex());
                buffer.writeLongLE(playerTeam.getCharacterData().getPlayerEntity().getEntityGameId());
                buffer.writeByte(playerTeam.getRemainItemUseTimes());
                if(this.hasInterruption){
                    buffer.writeByte(0);//interruption
                }
                buffer.writeBoolean(playerTeam.isPvpRank());
                if(playerTeam.isPvpRank()){
                    buffer.writeByte(playerTeam.getPvpRankLevel().getType());
                    buffer.writeShortLE(playerTeam.getPvpRankLevel().getLocalStringIndex());
                    buffer.writeByte(playerTeam.getStreakTimes());
                }
                Codecs.SKIN_CODEC_1.encode(buffer, playerTeam.getCharacterData());
                return;
            case WILD:
                return;
            case TRAINER:
                TrainerTeam trainerTeam = (TrainerTeam) object;
                buffer.writeByte(trainerTeam.getTrainerRegionIndexId());
                buffer.writeShortLE(trainerTeam.getTrainerNameIndexId());
                buffer.writeByte(trainerTeam.getRemainItemUseTimes());//ItemLimitUseTimes
                return;
            case CUSTOM_TRAINER:
                CustomTrainerTeam customTrainerTeam = (CustomTrainerTeam) object;
                buffer.writeByte(customTrainerTeam.getTrainerRegionIndexId());
                buffer.writeByte(customTrainerTeam.getTrainerNameIndex());
                buffer.writeByte(customTrainerTeam.getTrainerAnimationIndex());
                buffer.writeIntLE(customTrainerTeam.getTrainerNameLoacalStringIndex());
                buffer.writeByte(object.getRemainItemUseTimes());
                return;
            case COOPERATIVE_NPC:
            case COOPERATIVE_PLAYERS:
                CooperativeTeam cooperativeTeam = (CooperativeTeam) object;
                Map<Byte,DebutBattleTeam> coordinateActiveTeamMap = cooperativeTeam.getCoordinateActiveTeamMap();
                byte coordinateActiveTeamAmount = (byte) coordinateActiveTeamMap.size();
                buffer.writeByte(coordinateActiveTeamAmount);
                for(byte i = 0; i < coordinateActiveTeamAmount; i++) {
                    buffer.writeByte(coordinateActiveTeamMap.get(i).getTeamOriginIndex());//index
                    buffer.writeByte(coordinateActiveTeamMap.get(i).getTeamCurrentIndex());
                    buffer.writeByte(0);//unuse
                    encode(buffer,coordinateActiveTeamMap.get(i).getBattleTeam());
                }
                return;
            case NULL:
                return;
            default:
                break;
        }
    }
}
