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
public final class TradePokemonPacket extends IncomingPacket {
    private short position;
    private short action;
    private byte containerId;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != Short.BYTES * 2 + Byte.BYTES) {
            throw new IllegalArgumentException("非法交易宝可梦封包长度: " + buffer.readableBytes());
        }
        position = buffer.readShortLE();
        action = buffer.readShortLE();
        containerId = buffer.readByte();
    }

    @Override
    public void handle(Session session) {
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || !TradeManager.setPokemon(manager, containerId & 0xFF, position,
                action, gameServerService)) {
            log.warn("忽略非法交易宝可梦操作: containerId={}, position={}, action={}",
                    containerId & 0xFF, position, action);
        }
    }
}
