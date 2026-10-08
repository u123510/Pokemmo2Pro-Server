package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.battle.SmogonType;

public class LimitSmogonCodec implements ObjectCodec<SmogonType>{
    @Override
    public SmogonType decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void decode(ByteBufEx buffer, SmogonType object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, SmogonType object) {
        buffer.writeByte(object.getType());
        if(object.getIsAffectsDungeons())
            //写入hashMap的key
            buffer.writeByte(0);
    }
}
