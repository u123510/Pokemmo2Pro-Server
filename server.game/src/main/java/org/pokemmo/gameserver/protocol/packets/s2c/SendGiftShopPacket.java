package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.pokemmo.gameserver.game.giftshop.GiftShopItem;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.util.Collections;
import java.util.List;

/**
 * 礼品商城物品列表下行封包 (S2C 0x70, 对应客户端 f.N70)
 */
@NoArgsConstructor
@AllArgsConstructor
public class SendGiftShopPacket extends OutgoingPacket {
    private List<GiftShopItem> items;
    private byte[] keyBytes;

    public SendGiftShopPacket(List<GiftShopItem> items) {
        this.items = items;
        this.keyBytes = new byte[0];
    }

    @Override
    public void encode(ByteBufEx buffer) {
        // 1. Web key 字节长度 (unsigned byte) 及内容
        if (keyBytes != null && keyBytes.length > 0) {
            buffer.writeByte(keyBytes.length);
            buffer.writeBytes(keyBytes);
        } else {
            buffer.writeByte(0);
        }

        // 2. 商品条目数量 (short LE)
        List<GiftShopItem> itemList = items != null ? items : Collections.emptyList();
        buffer.writeShortLE(itemList.size());

        // 3. 逐条写入每个商品数据 (对应客户端 f.N70.Oj0)
        for (GiftShopItem item : itemList) {
            buffer.writeIntLE(item.getId());
            buffer.writeByte(item.getTargetType());
            buffer.writeByte(item.getCategory());
            buffer.writeShortLE(item.getItemIndexId());
            buffer.writeIntLE(item.getPrice());
            buffer.writeIntLE(item.getOriginalPrice());
            buffer.writeIntLE(item.getQuantity());
            buffer.writeIntLE(item.getParam1());
            buffer.writeIntLE(item.getReleaseTime());
            buffer.writeByte(item.getSeasonal());
            buffer.writeByte(item.getFlag2());
            if (item.isLimitedTime()) {
                buffer.writeByte(1);
                buffer.writeByte(item.getStartMonth());
                buffer.writeByte(item.getStartDay());
                buffer.writeByte(item.getEndMonth());
                buffer.writeByte(item.getEndDay());
            } else {
                buffer.writeByte(0);
            }
            buffer.writeIntLE(item.getFeaturedOrder());
        }
    }
}
