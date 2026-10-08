package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.kick.KickType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
public class SendKickInGamePacket extends OutgoingPacket {
    private final KickType kickType;
    private final byte[][] kickData;
    public SendKickInGamePacket(KickType kickType) {
        this.kickType = kickType;
        this.kickData = null;
    }
    public SendKickInGamePacket(KickType kickType, byte[][] kickData) {
        this.kickType = kickType;
        this.kickData = kickData;
    }
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(kickType.getType());
        if(kickType == KickType.TAMPERING) {
            buffer.writeByte(kickData.length);
            for (byte[] data : kickData) {
                buffer.writeByte(data.length);
                buffer.writeBytes(data);
            }
        }
    }
}
