package org.pokemmo.gameserver.codecs;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.battle.BattleStateType;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.battle.BattleBasisInfo;
import org.pokemmo.gameserver.game.battle.SmogonType;
import org.pokemmo.gameserver.game.battle.TimedEvent;
@RequiredArgsConstructor
public class BattleBassisInfoCodec implements ObjectCodec<BattleBasisInfo> {
    private final LimitSmogonCodec limitSmogonCodec = new LimitSmogonCodec();
    private final TimeEventCodec timeEventCodec = new TimeEventCodec();
    private final byte factionAmount;
    private final byte playerFactionIndex;
    private final byte selfTeamInDebutIndex;
    private final boolean isReconnect;
    private final boolean isSpectate;
    private final BattleStateType battleStateType;//对于玩家队伍而言的战斗状态机，预览，等待行动，开始战斗
    @Override
    public BattleBasisInfo decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, BattleBasisInfo object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, BattleBasisInfo object) {
        //战斗基础信息
        buffer.writeByte(factionAmount);//factionAmount
        buffer.writeByte(playerFactionIndex);
        buffer.writeByte(selfTeamInDebutIndex);
        buffer.writeByte(object.getBattleFormatType().getType());//对战形式 单打双打
        buffer.writeByte(object.getBattleFormType().getType());//对战层级 普通
        buffer.writeByte(object.getBattleFacilityType().getType());//battleFacilityIndex
        buffer.writeIntLE(object.getBattleAlreadyRunTime());//战斗已经进行的时间
        buffer.writeShortLE(object.getBattleRoundAmount());//battleRoundAmount
        buffer.writeByte(battleStateType.getType());//BattleStateValue
        buffer.writeBoolean(isReconnect);//isReconnect
        buffer.writeBoolean(isSpectate);//isSpectate
        //战斗状态信息
        int battleInfoFlag = 0;
        if(object.isCanRun())
            battleInfoFlag = battleInfoFlag  | 4;
        if(object.isUseEmemyAngle())
            battleInfoFlag = battleInfoFlag  | 65535;
        if(object.isHasInterruption())
            battleInfoFlag = battleInfoFlag  | 262144;
        if(object.isCanUseItem())
            battleInfoFlag = battleInfoFlag  | 2;
        if(object.isHasLimitMinute())
            battleInfoFlag = battleInfoFlag  | 8;
        if(object.isPvp())
            battleInfoFlag = battleInfoFlag  | 16;
        if(object.isHasLimitSmogon())
            battleInfoFlag = battleInfoFlag  | 32;
        if(object.isHasTimeEvent())
            battleInfoFlag = battleInfoFlag  | 64;
        if(object.isReloadBattleMusic())
            battleInfoFlag = battleInfoFlag  | 128;
        if(object.isPvpRank())
            battleInfoFlag = battleInfoFlag  | 256;
        if(object.isCanSurrender())
            battleInfoFlag = battleInfoFlag  | 512;
        if(object.isReloadPokemonContainerType())
            battleInfoFlag = battleInfoFlag  | 1024;
        if(object.isReloadBattleStatsBroadcastMode())
            battleInfoFlag = battleInfoFlag  | 2048;
        if(object.isReloadRandomSeed())
            battleInfoFlag = battleInfoFlag  | 4096;
        if(object.isIgnoreBattleAnimation())
            battleInfoFlag = battleInfoFlag  | 8192;
        if(object.isHasUnuse())
            battleInfoFlag = battleInfoFlag  | 16384;
        if(object.isHasBossBatLimitLevel())
            battleInfoFlag = battleInfoFlag  | 32768;
        buffer.writeIntLE(battleInfoFlag);
        if(object.isCanUseItem())
            buffer.writeByte(object.getUseItemLimitAmount());
        if(object.isHasLimitMinute())
            buffer.writeByte(object.getLimitMinutes());
        if(object.isPvp())
            buffer.writeByte(object.getPvpLevel());
        if(object.isHasLimitSmogon()){
            SmogonType[] limitSmogons = object.getLimitSmogons();
            buffer.writeByte(limitSmogons.length);
            for(SmogonType smogon : limitSmogons){
                limitSmogonCodec.encode(buffer,smogon);
            }
        }
        if(object.isHasTimeEvent()) {
            TimedEvent[] selfSideTimeEvent = object.getSelfSideTimeEvent();
            TimedEvent[] enemySideTimeEvent = object.getEnemySideTimeEvent();
            buffer.writeByte(selfSideTimeEvent.length);
            for(TimedEvent timeEvent : selfSideTimeEvent){
                timeEventCodec.encode(buffer,timeEvent);
            }
            buffer.writeByte(enemySideTimeEvent.length);
            for(TimedEvent timeEvent : enemySideTimeEvent){
                timeEventCodec.encode(buffer,timeEvent);
            }
        }
        if(object.isReloadBattleMusic()){
            buffer.writeByte(object.getBattleMusicRegionIndexId());
            buffer.writeShortLE(object.getBattleMusicIndexId());
        }
        if(object.isPvpRank())
            buffer.writeByte(object.getPvpRankLevelType().getType());
        if(object.isCanSurrender())
            buffer.writeByte(object.getSurrenderLimitArround());
        if(object.isReloadPokemonContainerType())
            buffer.writeByte(object.getBattlePokemonContainerType().getType());
        if(object.isReloadBattleStatsBroadcastMode()) {
            buffer.writeByte(object.getBattleStatsBroadcastMode().getType());
            byte pokemonStatBisMask = 0;
            for(int i=0;i<8;i++){
                if(object.getIsShowPokemonAbilityValue()[i]){
                    pokemonStatBisMask = (byte)(pokemonStatBisMask | (1 << i));
                }
            }
            buffer.writeByte(pokemonStatBisMask);
        }
        if(object.isReloadRandomSeed())
            buffer.writeIntLE(object.getRandomSeed());
        if(object.isHasUnuse())
            buffer.writeByte(object.getUnuse());
        if(object.isHasBossBatLimitLevel())
            buffer.writeByte(object.getBossBatLimitLevel());
        if(battleStateType.getIsPreview()){
            buffer.writeBoolean(true);//enemyConfirmed2
            boolean isLimitTime = true;
            buffer.writeBoolean(isLimitTime);
            if(isLimitTime){
                buffer.writeBoolean(true);//isTimeLimitEnabled
                buffer.writeShortLE(10);//presetTotalTime
                buffer.writeShortLE(20);//oneTurnMaxLimitTime
            }
        }
    }
}
