package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.shop.ShopRequest;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** OpenMMO extension S2C 0xDC, version 1. Sent only after explicit client negotiation. */
public final class SendShopControlPacket extends OutgoingPacket {
    private static final int MAGIC = 0x7E;
    private final int kind;
    private final long quoteId;
    private final int requestId;
    private final int action;
    private final int status;
    private final String message;

    private SendShopControlPacket(int kind, long quoteId, int requestId, int action, int status, String message) {
        this.kind = kind;
        this.quoteId = quoteId;
        this.requestId = requestId;
        this.action = action;
        this.status = status;
        this.message = message;
    }

    public static SendShopControlPacket capabilities() {
        return new SendShopControlPacket(0, 0, 0, 0, 0, "");
    }

    public static SendShopControlPacket result(ShopRequest request, int status, String message) {
        return new SendShopControlPacket(1, request.quoteId(), request.requestId(), request.action(), status, message);
    }

    public static SendShopControlPacket closed(long quoteId, String reason) {
        return new SendShopControlPacket(2, quoteId, 0, 0, 0, reason);
    }

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(MAGIC);
        buffer.writeByte(1);
        buffer.writeByte(kind);
        if (kind == 0) return;
        buffer.writeLongLE(quoteId);
        if (kind == 1) {
            buffer.writeIntLE(requestId);
            buffer.writeByte(action);
            buffer.writeByte(status);
        }
        buffer.writeUtf16LE(message);
    }
}
