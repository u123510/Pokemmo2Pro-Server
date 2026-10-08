package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendGameMailPacket extends OutgoingPacket{
    private final short recvMailReordCount;
    private final short unreadMailCount;
    private final short sendMailReordCount;

    public SendGameMailPacket() {
        this((short) 0, (short) 0, (short) 0);
    }

    public SendGameMailPacket(short recvMailReordCount, short unreadMailCount,
                              short sendMailReordCount) {
        this.recvMailReordCount = recvMailReordCount;
        this.unreadMailCount = unreadMailCount;
        this.sendMailReordCount = sendMailReordCount;
    }

    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeShortLE(recvMailReordCount);
        buffer.writeShortLE(unreadMailCount);
        buffer.writeShortLE(sendMailReordCount);
    }
}
