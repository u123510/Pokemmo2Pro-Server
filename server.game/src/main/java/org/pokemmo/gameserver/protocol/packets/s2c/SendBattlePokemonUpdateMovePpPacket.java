package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendBattlePokemonUpdateMovePpPacket extends OutgoingPacket {
    private long pokemonId;
    private byte movePos;
    private byte moveRemainPp;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(pokemonId);
        buffer.writeByte(movePos);
        buffer.writeByte(moveRemainPp);
    }
    public SendBattlePokemonUpdateMovePpPacket(long pokemonId, int movePos, int moveRemainPp){
        this.pokemonId = pokemonId;
        this.movePos = (byte) movePos;
        this.moveRemainPp = (byte) moveRemainPp;
    }
}
