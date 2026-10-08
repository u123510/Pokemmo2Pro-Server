package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public final class TradeItemPacket extends IncomingPacket {
    private static final int BASIC_PAYLOAD_SIZE = Long.BYTES + Short.BYTES;
    private static final int SLOT_PAYLOAD_SIZE = Short.BYTES + BASIC_PAYLOAD_SIZE;

    private short slot;
    private long itemId;
    private short amount;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        // 0x24 is long+short; the 0x53 variant prefixes an unused slot short.
        int payloadSize = buffer.readableBytes();
        if (payloadSize != BASIC_PAYLOAD_SIZE && payloadSize != SLOT_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("非法交易道具封包长度: " + payloadSize);
        }
        if (payloadSize == SLOT_PAYLOAD_SIZE) {
            slot = buffer.readShortLE();
        } else {
            slot = 0;
        }
        itemId = buffer.readLongLE();
        amount = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) {
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || !TradeManager.setItem(manager, slot, itemId, amount, gameServerService)) {
            log.warn("忽略非法交易道具操作: slot={}, itemId={}, amount={}", slot, itemId, amount);
        }
    }
}
