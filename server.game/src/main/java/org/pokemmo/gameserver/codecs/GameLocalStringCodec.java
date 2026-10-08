package org.pokemmo.gameserver.codecs;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.string.GameLocalFormatString;

public class GameLocalStringCodec implements ObjectCodec<GameLocalFormatString>{
    @Override
    public GameLocalFormatString decode(ByteBufEx buffer) {throw new UnsupportedOperationException();}

    @Override
    public void decode(ByteBufEx buffer, GameLocalFormatString object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(ByteBufEx buffer, GameLocalFormatString object) {
        buffer.writeByte(object.getReplaceIndex());
        buffer.writeByte(object.getStringType());
        if((object.getStringType() & 128)!= 0)
            buffer.writeByte(object.getRegionIndex());
        if(object.getStringType()!=5) {
            if(object.getStringType()== 30){
                buffer.writeLongLE(object.getLongStringId());
                return;
            }
            if(object.getStringType()== 9 || object.getStringType()== 10 || object.getStringType()== 17) {
                buffer.writeIntLE(object.getIntStringId());
                return;
            }
            if(object.getStringType()!=18) {
                buffer.writeByte(object.getStringIndexArray().length);
                for(short stringIndex : object.getStringIndexArray()){
                    buffer.writeShortLE(stringIndex);
                }
                return;
            }
        }
        buffer.writeUtf16LE(object.getNotificationString());
    }
}
