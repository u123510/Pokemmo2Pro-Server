package org.pokemmo.gameserver.codecs;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.instance.GameInstance;

public class InstanceInfoCodec implements ObjectCodec<GameInstance> {
    @Override
    public GameInstance decode(ByteBufEx buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(ByteBufEx buffer, GameInstance object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, GameInstance object) {
        buffer.writeByte(object.getInstanceType());
        buffer.writeIntLE((int)object.getNextInstanceTime());
        buffer.writeShortLE(object.getFinishTimes());
    }
    //getNextInstanceTime().toEpochSecond(ZoneOffset.UTC


}
