package org.pokemmo.gameserver.protocol.packets.c2s;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public final class TradeMoneyPacket extends IncomingPacket {
    private int money;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != Integer.BYTES) {
            throw new IllegalArgumentException("非法交易金额封包长度: " + buffer.readableBytes());
        }
        money = buffer.readIntLE();
    }

    @Override
    public void handle(Session session) {
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || !TradeManager.setMoney(manager, money)) {
            log.warn("忽略非法交易金额: {}", money);
        }
    }
}
