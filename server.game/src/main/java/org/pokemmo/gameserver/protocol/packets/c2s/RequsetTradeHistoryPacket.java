package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGtlTradeHistoryPacket;
import org.pokemmo.gameserver.services.gtl.GtlPurchaseHistoryQuery;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class RequsetTradeHistoryPacket extends IncomingPacket {
    @Inject
    private GtlPurchaseHistoryQuery purchaseHistoryQuery;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Unexpected GTL trade-history payload size: " + buffer.readableBytes());
        }
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("Ignoring GTL trade-history request without a loaded character");
            session.send(new SendGtlTradeHistoryPacket(null));
            return;
        }

        long characterId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        session.send(new SendGtlTradeHistoryPacket(
                purchaseHistoryQuery.getPurchaseHistory(characterId)));
    }
}
