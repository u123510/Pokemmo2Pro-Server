package org.pokemmo.gameserver.codecs;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.battle.TimedEvent;

public class TimeEventCodec implements ObjectCodec<TimedEvent>{
    @Override
    public TimedEvent decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(ByteBufEx buffer, TimedEvent object) {
        throw new UnsupportedOperationException();
    }

    @Override
        public void encode(ByteBufEx buffer, TimedEvent object) {
            boolean resetTimeLimit = object.isResetTimeLimit();
            buffer.writeBoolean(resetTimeLimit);
            if(resetTimeLimit){
                buffer.writeBoolean(object.isShouldUpdateTimeLimit());
                buffer.writeShortLE(object.getPlayerEachRoundMaxTimeLimit());
                buffer.writeShortLE(object.getPlayerEachRoundMaxTimeLimit());
            }
        }
}
