package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.gameserver.game.shop.ShopRequest;
import org.pokemmo.gameserver.game.shop.ShopService;
import org.pokemmo.gameserver.game.shop.ShopSessions;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** OpenMMO extension C2S 0xDC; no collision with legacy 0x23/0x24 trade requests. */
public final class ShopControlPacket extends IncomingPacket {
    private static final int MAGIC = 0x7E;
    private final ShopService shops;
    private int action;
    private long quoteId;
    private ShopRequest request;

    @Inject
    public ShopControlPacket(ShopService shops) {
        this.shops = shops;
    }

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() < 3 || buffer.readUnsignedByte() != MAGIC
                || buffer.readUnsignedByte() != 1) {
            throw new IllegalArgumentException("商店扩展协议 magic 或版本无效");
        }
        action = buffer.readUnsignedByte();
        int expected = switch (action) {
            case 0 -> 0;
            case ShopRequest.BUY -> 16;
            case ShopRequest.SELL -> 22;
            case 3 -> 8;
            default -> throw new IllegalArgumentException("未知商店扩展操作");
        };
        if (buffer.readableBytes() != expected) {
            throw new IllegalArgumentException("商店扩展请求长度无效");
        }
        if (action == 0) return;
        quoteId = buffer.readLongLE();
        if (quoteId <= 0) {
            throw new IllegalArgumentException("商店报价编号无效");
        }
        if (action == 3) return;
        int requestId = buffer.readIntLE();
        long targetId = action == ShopRequest.BUY ? buffer.readUnsignedShortLE() : buffer.readLongLE();
        int amount = buffer.readUnsignedShortLE();
        request = new ShopRequest(action, quoteId, requestId, targetId, amount);
    }

    @Override
    public void handle(Session session) {
        switch (action) {
            case 0 -> ShopSessions.negotiate(session);
            case 3 -> ShopSessions.closeRequested(session, quoteId);
            default -> shops.handle(session, request);
        }
    }
}
