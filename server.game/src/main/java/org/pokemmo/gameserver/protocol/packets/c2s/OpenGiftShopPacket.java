package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.giftshop.GiftShopItem;
import org.pokemmo.gameserver.game.giftshop.GiftShopManager;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGiftShopPacket;
import org.pokemmo.gameserver.script.ScriptManager;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.Collections;
import java.util.List;

/**
 * 客户端打开礼品商城请求封包 (C2S 0x70, 对应客户端 f.F80)
 */
@Slf4j
public class OpenGiftShopPacket extends IncomingPacket {

    @Inject
    private ScriptManager scriptManager;

    @Override
    public void decode(ByteBufEx buffer) {
        // C2S 0x70 无附加 payload，仅包含 1 字节 opcode
    }

    @Override
    public void handle(Session session) throws Exception {
        GiftShopManager giftShopManager = scriptManager != null ? scriptManager.getGiftShopManager() : null;
        List<GiftShopItem> items = giftShopManager != null ? giftShopManager.getItems() : Collections.emptyList();
        log.debug("响应客户端打开礼品商城: session={}, itemsCount={}", session, items.size());
        session.send(new SendGiftShopPacket(items));
    }
}
