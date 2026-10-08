package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class CloseMailWidgetPacket extends IncomingPacket {

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("关闭邮箱请求不应携带数据");
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager != null) {
            characterManager.getInteractManager().setMailWidgetOpen(false);
        }
    }
}
