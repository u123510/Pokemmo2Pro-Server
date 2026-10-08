package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.battle.BattleSpectatingService;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class BattleSpectatingReturnPacket extends IncomingPacket {
    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("观战退出封包不应包含字段");
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager != null && characterManager.getBattleManager() != null
                && characterManager.getBattleManager().isSpectator(session)
                ) {
            BattleSpectatingService.leave(characterManager);
        }
    }
}
