package org.pokemmo.gameserver.protocol.packets.s2c;

import java.util.Objects;

import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendGtlActionResultPacket extends OutgoingPacket {
    private final GtlActionResult result;

    public SendGtlActionResultPacket(GtlActionResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(result.getCode());
    }
}
