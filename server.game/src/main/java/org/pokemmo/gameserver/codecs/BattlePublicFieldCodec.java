package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.battle.BattlePublicFieldInfo;

public class BattlePublicFieldCodec implements ObjectCodec<BattlePublicFieldInfo>{
    @Override
    public BattlePublicFieldInfo decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, BattlePublicFieldInfo object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, BattlePublicFieldInfo object) {
        int flag = 0;
        if(object.isHasWeather()){
            flag |= 1;
        }
        if(object.isHasTrickRoom()){
            flag |= 2;
        }
        if(object.isHasWonderRoom()){
            flag |= 4;
        }
        if(object.isHasMagicRoom()){
            flag |= 8;
        }
        if(object.isHasGravityField()){
            flag |= 16;
        }
        if(object.isHasInverseBattleEffect()){
            flag |= 64;
        }
        if(object.isHasDamageTypeReverseEffect()){
            flag |= 128;
        }
        buffer.writeIntLE(flag);
        if(object.isHasWeather()){
            buffer.writeByte(object.getBattleWeatherType().getType());
            buffer.writeIntLE(object.getWeatherRemainRound());
        }
        if(object.isHasTrickRoom()){
            buffer.writeByte(object.getTrickRoomRemainRound());
        }
        if(object.isHasWonderRoom()){
            buffer.writeByte(object.getWonderRoomRemainRound());
        }
        if(object.isHasMagicRoom()){
            buffer.writeByte(object.getMagicRoomRemainRound());
        }
        if(object.isHasGravityField()){
            buffer.writeByte(object.getGravityFieldRemainRound());
        }
        if(object.isHasInverseBattleEffect()){
            buffer.writeByte(object.getInverseBattleEffectRemainRound());
        }
        if(object.isHasDamageTypeReverseEffect()){
            buffer.writeByte(object.getDamageTypeReverseEffectRemainRound());
        }
    }
}
