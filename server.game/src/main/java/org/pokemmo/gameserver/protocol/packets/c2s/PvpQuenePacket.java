package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class PvpQuenePacket extends IncomingPacket {
    private byte pvpLevelAmount;
    private byte pvpLevel;
    private long tournamentId;
    @Override
    public void decode(ByteBufEx buffer) {
       pvpLevelAmount = buffer.readByte();
       if(pvpLevelAmount>0){
           pvpLevel = buffer.readByte();
           byte unk = buffer.readByte();
       }
       else{
           tournamentId = buffer.readLongLE();
           byte unk = buffer.readByte();
       }
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
