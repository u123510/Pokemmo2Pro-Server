package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendCharacterMovePacket extends OutgoingPacket {
    private final long characterId;
    private final byte bankId;
    private final byte mapId;
    private final byte x;
    private final byte y;
    private final byte direction;
    private final byte flag;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
    buffer.writeLongLE(characterId);
    buffer.writeByte(bankId);
    buffer.writeByte(mapId);
    buffer.writeByte(x);
    buffer.writeByte(y);
    buffer.writeByte(direction);
    buffer.writeByte(flag);
    }
}
