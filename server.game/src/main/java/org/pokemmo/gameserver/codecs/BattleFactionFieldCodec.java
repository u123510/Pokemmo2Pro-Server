package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.battle.BattleFactionFieldInfo;

public class BattleFactionFieldCodec implements ObjectCodec<BattleFactionFieldInfo> {

    @Override
    public BattleFactionFieldInfo decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(ByteBufEx buffer, BattleFactionFieldInfo object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, BattleFactionFieldInfo object) {
        int fieldFlag = 0;
        if(object.isHasFieldInit()){
            fieldFlag = fieldFlag |1;
        }
        if(object.isHasPledgeFieldOfFireField()) {
            fieldFlag = fieldFlag | 8;
        }
        if(object.isHasPledgeRainbowField()){
            fieldFlag = fieldFlag | 16;
        }
        if(object.isHasPledgeSwampField()){
            fieldFlag = fieldFlag | 32;
        }
        if(object.isHasPledgeCureField()){
            fieldFlag = fieldFlag | 64;
        }
        if(object.isInMaelstrom()){
            fieldFlag = fieldFlag | 128;
        }
        if(object.isHasTailWindField()){
            fieldFlag = fieldFlag | 256;
        }
        if(object.isHasEnemyCommand()){
            fieldFlag = fieldFlag | 512;
        }
        if(object.isHasSelfCommand()){
            fieldFlag = fieldFlag | 1024;
        }
        if(object.isHasSafeGuardField()){
            fieldFlag = fieldFlag | 2048;
        }
        if(object.isHasMistField()){
            fieldFlag = fieldFlag | 4096;
        }
        if(object.isHasLuckyChantField()){
            fieldFlag = fieldFlag | 8192;
        }
        if(object.isHasStrongWindsField()){
            fieldFlag = fieldFlag | 16384;
        }
        buffer.writeIntLE(fieldFlag);
        if(object.isHasFieldInit()){
            buffer.writeByte(object.getTrapInfo().size());
            for(Byte key : object.getTrapInfo().keySet()){
                buffer.writeByte(key);
                buffer.writeByte(object.getTrapInfo().get(key));
            }
        }
        if(object.isHasPledgeFieldOfFireField())
            buffer.writeByte(object.getPledgeFieldOfFireReaminRound());
        if(object.isHasPledgeRainbowField())
            buffer.writeByte(object.getPledgeRainbowRemainRound());
        if(object.isHasPledgeSwampField())
            buffer.writeByte(object.getPledgeSwampRemainRound());
        if(object.isHasPledgeCureField())
            buffer.writeByte(object.getPledgeCureRemainRound());
        if(object.isHasTailWindField())
            buffer.writeByte(object.getTailWindRemainRound());
        if(object.isHasEnemyCommand()) {
            buffer.writeByte(object.getEnemyCommandTotalRound());
            buffer.writeShortLE(object.getEnemyCommandUseRound());
            buffer.writeShortLE(object.getEnemyCommandSkillIndex());
            buffer.writeByte(object.getEnemyCommandRemainTurns());
        }
        if(object.isHasSelfCommand()){
            buffer.writeByte(object.getSelfCommandTotalRound());
            buffer.writeShortLE(object.getSelfCommandUseRound());
            buffer.writeShortLE(object.getSelfCommandSkillIndex());
            buffer.writeByte(object.getSelfCommandRemainTurns());
        }
        if(object.isHasSafeGuardField())
            buffer.writeByte(object.getSafeguardRemainRound());
        if(object.isHasMistField())
            buffer.writeByte(object.getMistRemainRound());
        if(object.isHasLuckyChantField())
            buffer.writeByte(object.getLuckyChantRemainRound());
    }
}
