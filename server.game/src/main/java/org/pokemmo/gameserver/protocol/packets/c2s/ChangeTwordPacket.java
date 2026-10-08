package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.shop.ShopSessions;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;

public class ChangeTwordPacket extends IncomingPacket {
    private byte toward;
    private PlayerEntity player;
    @Override
    public void decode(ByteBufEx buffer) {
        // The client sends movement flags in the same byte on some paths;
        // only the low two bits are the persistent cardinal direction.
        toward = (byte) (buffer.readByte() & 0x03);
    }
    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        player= characterManager.getCharacterData().getPlayerEntity();
        if (player.getToward() != toward) {
            ShopSessions.close(session, "角色改变朝向，商店已关闭", true);
        }
        player.setToward(toward);
        characterManager.broadcastPlayerToward();
    }
}
