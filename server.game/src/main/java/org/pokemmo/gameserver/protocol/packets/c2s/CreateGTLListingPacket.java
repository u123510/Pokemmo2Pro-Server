package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.game.gtl.GtlListingType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGtlActionResultPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGTLPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemovePokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.gtl.GtlListingCreateResult;
import org.pokemmo.gameserver.services.gtl.GtlListingService;
import org.pokemmo.gameserver.services.gtl.GtlQueryService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class CreateGTLListingPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = 15;

    private byte listingTypeValue;
    private long objectId;
    private int unitPrice;
    private short amount;

    @Inject
    private GameServerService gameServerService;

    @Inject
    private GtlListingService gtlListingService;

    @Inject
    private GtlQueryService gtlQueryService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Unexpected GTL listing payload size: " + buffer.readableBytes());
        }
        listingTypeValue = buffer.readByte();
        objectId = buffer.readLongLE();
        unitPrice = buffer.readIntLE();
        amount = buffer.readShortLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("Ignoring GTL listing request without a loaded character");
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }

        GtlListingType listingType = GtlListingType.getByType(listingTypeValue & 0xFF);
        if (listingType == null || objectId <= 0 || unitPrice <= 0 || amount <= 0) {
            log.warn("Rejecting invalid GTL listing request: type={}, objectId={}, price={}, amount={}",
                    listingTypeValue & 0xFF, objectId, unitPrice, amount);
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        if ((listingType == GtlListingType.ITEM && TradeManager.isItemOffered(characterManager, objectId))
                || (listingType == GtlListingType.POKEMON
                && TradeManager.isPokemonOffered(characterManager, objectId))) {
            log.warn("拒绝上架交易中的资产: type={}, objectId={}", listingType, objectId);
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }
        GtlListingCreateResult result = switch (listingType) {
            case POKEMON -> gtlListingService.createPokemonGtlListing(
                    characterId, objectId, unitPrice, amount);
            case ITEM -> gtlListingService.createItemGtlListing(
                    characterId, objectId, unitPrice, amount,
                    characterManager.getSnowflakeIdGenerator());
        };

        session.send(new SendGtlActionResultPacket(result.result()));
        if (result.result() != GtlActionResult.SUCCESS) {
            return;
        }

        characterManager.getCharacterData().setMoney(result.remainingMoney());
        UpdateCharacterSelector moneyUpdate = new UpdateCharacterSelector.Builder()
                .setRefreshMoney(true)
                .setCharacterData(characterManager.getCharacterData())
                .build();

        SendGTLPacket refresh = RequestGTLPacket.createCurrentPageRefresh(
                session, gtlQueryService);
        if (listingType == GtlListingType.ITEM) {
            var inventory = gameServerService.getInventory();
            if (inventory == null) {
                log.error("Unable to refresh inventory after ITEM GTL listing: characterId={}",
                        characterId);
                session.send(new SendUpdatePlayerInfo(moneyUpdate));
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
        if (refresh == null) {
            session.send(
                    new SendUpdatePlayerInfo(moneyUpdate),
                    new SendRemovePokemonPacket(PokemonContainerType.PC, objectId));
            return;
        }
        session.send(
                new SendUpdatePlayerInfo(moneyUpdate),
                new SendRemovePokemonPacket(PokemonContainerType.PC, objectId),
                refresh);
    }
}
