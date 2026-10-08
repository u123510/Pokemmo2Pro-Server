package org.pokemmo.gameserver.codecs;

import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.string.BattleString;
import org.pokemmo.gameserver.game.string.GameLocalFormatString;


public class BattleStringCodec implements ObjectCodec<BattleString>{
    @Override
    public BattleString decode(ByteBufEx buffer) {throw new UnsupportedOperationException();}
    @Override
    public void decode(ByteBufEx buffer, BattleString object) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void encode(ByteBufEx buffer, BattleString object) {
        buffer.writeByte(object.getBattleStringType().getType()-1);
        switch (object.getBattleStringType()) {
            case LOCAL_STRING_TYPE:
                buffer.writeInt(object.getStringIndexId());
                buffer.writeIntLE(object.getGameLocalStrings().length);
                for(GameLocalFormatString gameLocalString : object.getGameLocalStrings()) {
                    GameLocalStringCodec codec = new GameLocalStringCodec();
                    codec.encode(buffer, gameLocalString);
                }
                break;
            case UNKNOWN_TYPE:
                buffer.writeByte(object.getUnkbyte());
                buffer.writeIntLE(object.getUnkshortArray().length);
                for(short unkshort : object.getUnkshortArray())
                    buffer.writeShortLE(unkshort);
                break;
            case UNKNOWN_TYPE_2:
                buffer.writeByte(object.getUnkbyte2());
                buffer.writeByte(object.getUnkbyte3());
                buffer.writeShort(object.getUnkshort());
                break;
            case UNKNOWN_TYPE_3:
                buffer.writeByte(object.getUnkbyte4());
                buffer.writeByte(object.getLocalStringType().getType());
                buffer.writeShort(object.getUnkshort2());
                buffer.writeShort(object.getUnkshort3());
                break;
        }
    }
}
