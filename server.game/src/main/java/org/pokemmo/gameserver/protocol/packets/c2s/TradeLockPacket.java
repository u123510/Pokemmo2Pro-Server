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
public final class TradeLockPacket extends IncomingPacket {
    private byte action;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != Byte.BYTES) {
            throw new IllegalArgumentException("非法交易状态封包长度: " + buffer.readableBytes());
        }
        action = buffer.readByte();
    }

    @Override
    public void handle(Session session) {
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || !TradeManager.setLock(manager, action, gameServerService)) {
            log.warn("忽略非法交易锁定/确认操作: {}", action);
        }
    }
}
