package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.battle.BattleSpectatingService;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** C2S 0x34: request to spectate a nearby player's active PvP battle. */
public final class BattleSpectateRequestPacket extends IncomingPacket {
    private long targetCharacterId;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != Long.BYTES) {
            throw new IllegalArgumentException("观战请求必须只包含 8 字节玩家 Object ID");
        }
        targetCharacterId = buffer.readLongLE();
        if (targetCharacterId <= 0) {
            throw new IllegalArgumentException("观战目标玩家 Object ID 非法");
        }
    }

    @Override
    public void handle(Session session) {
        CharacterManager spectator = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (spectator != null) {
            BattleSpectatingService.request(spectator, targetCharacterId);
        }
    }
}
