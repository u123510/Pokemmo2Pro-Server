package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.events.GameEvent;

public class GameEventFlagsCodec implements ObjectCodec<GameEvent>{
    @Override
    public GameEvent decode(ByteBufEx buffer) {throw new UnsupportedOperationException();}

    @Override
    public void decode(ByteBufEx buffer, GameEvent object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, GameEvent object) {
        if(object.getEventStatus() == 1){
            buffer.writeShortLE(object.getEventType().getEventFlagId());
            buffer.writeShortLE(object.getEventStatus());
        }
    }
}
