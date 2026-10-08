package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGtlActionResultPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGTLPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.gtl.GtlPurchaseResult;
import org.pokemmo.gameserver.services.gtl.GtlPurchaseService;
import org.pokemmo.gameserver.services.gtl.GtlQueryService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class PurchaseGTLListingPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = 10;

    private long listingId;
    private short amount;

    @Inject
    private GameServerService gameServerService;

    @Inject
    private GtlPurchaseService gtlPurchaseService;

    @Inject
    private GtlQueryService gtlQueryService;

    @Inject
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Unexpected GTL purchase payload size: " + buffer.readableBytes());
        }
        listingId = buffer.readLongLE();
        amount = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("Ignoring GTL purchase request without a loaded character");
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }
        if (listingId <= 0 || amount <= 0) {
            log.warn("Rejecting invalid GTL purchase request: listingId={}, amount={}",
                    listingId, amount);
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        GtlPurchaseResult result = gtlPurchaseService.purchaseGtlListing(
                characterId, listingId, amount, snowflakeIdGenerator);
        if (result.result() != GtlActionResult.SUCCESS) {
            // The client maps a successful 0x9C response to the listing-created message.
            // Purchases have no dedicated success response, so only report failures here.
            session.send(new SendGtlActionResultPacket(result.result()));
            return;
        }

        characterManager.getCharacterData().setMoney(result.remainingMoney());
        UpdateCharacterSelector moneyUpdate = new UpdateCharacterSelector.Builder()
                .setRefreshMoney(true)
                .setCharacterData(characterManager.getCharacterData())
                .build();
        SendGTLPacket refresh = RequestGTLPacket.createCurrentPageRefresh(
                session, gtlQueryService);
        if (result.item() != null) {
            var inventory = gameServerService.getInventory();
            if (inventory == null) {
                log.error("GTL item purchase succeeded but inventory container is missing: buyerId={}",
                        characterId);
                if (refresh == null) {
                    session.send(new SendUpdatePlayerInfo(moneyUpdate));
                } else {
                    session.send(new SendUpdatePlayerInfo(moneyUpdate), refresh);
                }
                return;
            }
            SendInventoryPacket inventoryRefresh = new SendInventoryPacket(
                    inventory,
                    gameServerService.getItemsByContainerAndCharacter(characterId, inventory));
            if (refresh == null) {
                session.send(new SendUpdatePlayerInfo(moneyUpdate), inventoryRefresh);
                return;
            }
            session.send(new SendUpdatePlayerInfo(moneyUpdate), inventoryRefresh, refresh);
            return;
        }
        if (result.pokemon() == null) {
            log.error("GTL purchase succeeded without a purchased object: buyerId={}, listingId={}",
                    characterId, listingId);
            return;
        }
        if (refresh == null) {
            session.send(
                    new SendUpdatePlayerInfo(moneyUpdate),
                    new SendAddPokemonPacket(result.pokemon()));
            return;
        }
        session.send(
                new SendUpdatePlayerInfo(moneyUpdate),
                new SendAddPokemonPacket(result.pokemon()),
                refresh);
    }
}
