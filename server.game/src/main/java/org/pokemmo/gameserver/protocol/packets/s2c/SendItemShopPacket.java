package org.pokemmo.gameserver.protocol.packets.s2c;

import java.util.List;

import org.pokemmo.gameserver.game.shop.ShopDefinition;
import org.pokemmo.gameserver.game.shop.ShopItem;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

/** S2C 0x23: native header with negotiated OpenMMO price/quote extension in flag 0x80. */
public final class SendItemShopPacket extends OutgoingPacket {
    private final ShopDefinition shop;
    private final long quoteId;

    private SendItemShopPacket(ShopDefinition shop, long quoteId) {
        this.shop = shop;
        this.quoteId = quoteId;
    }

    public static SendItemShopPacket readOnly(ShopDefinition shop) {
        if (shop == null) throw new IllegalArgumentException("店铺配置不能为空");
        return new SendItemShopPacket(shop, 0);
    }

    public static SendItemShopPacket trading(ShopDefinition shop, long quoteId) {
        if (shop == null || quoteId <= 0) throw new IllegalArgumentException("店铺配置或报价编号无效");
        return new SendItemShopPacket(shop, quoteId);
    }

    public static SendItemShopPacket closed() {
        return new SendItemShopPacket(null, 0);
    }

    @Override
    public void encode(ByteBufEx buffer) {
        if (shop == null) {
            buffer.writeByte(0xFF);
            return;
        }
        List<ShopItem> items = shop.buyItems();
        List<ShopItem> sellItems = shop.sellItems();
        int flags = quoteId == 0 ? 0 : 0x80 | (items.isEmpty() ? 0 : 1) | (sellItems.isEmpty() ? 0 : 6);
        buffer.writeByte(0);
        buffer.writeByte(flags);
        buffer.writeByte(0);
        buffer.writeShortLE(items.size());
        for (ShopItem item : items) {
            buffer.writeShortLE(item.itemId());
            buffer.writeShortLE(1);
            buffer.writeShortLE(0);
            buffer.writeIntLE(item.buyPrice());
        }
        if (quoteId != 0) {
            buffer.writeByte(1);
            buffer.writeLongLE(quoteId);
            buffer.writeShortLE(sellItems.size());
            for (ShopItem item : sellItems) {
                buffer.writeShortLE(item.itemId());
                buffer.writeIntLE(item.sellPrice());
            }
        }
    }
}
