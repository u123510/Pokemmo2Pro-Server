package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.db.jooq.tables.records.InventoryRecord;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class DestroyItemPacket extends IncomingPacket {
    private long itemId;
    private short destroyAmount;

    @Inject
    private GameServerService gameServerService;

    @Override
    public void decode(ByteBufEx buffer) {
        itemId = buffer.readLongLE();
        destroyAmount = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        if (TradeManager.isItemOffered(characterManager, itemId)) {
            return;
        }
        if (!gameServerService.removeInventoryItem(characterId, itemId, destroyAmount)) {
            return;
        }

        InventoryRecord inventory = gameServerService.getInventory();
        if (inventory != null) {
            session.send(new SendInventoryPacket(
                    inventory,
                    gameServerService.getItemsByContainerAndCharacter(characterId, inventory)
            ));
        }
    }
}
