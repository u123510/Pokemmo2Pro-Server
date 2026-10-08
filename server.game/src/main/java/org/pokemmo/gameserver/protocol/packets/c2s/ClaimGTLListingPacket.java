package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGtlActionResultPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGTLPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.services.gtl.GtlClaimResult;
import org.pokemmo.gameserver.services.gtl.GtlClaimService;
import org.pokemmo.gameserver.services.gtl.GtlQueryService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class ClaimGTLListingPacket extends IncomingPacket {
    private static final int MAX_LISTINGS = 0xFF;

    private List<Long> listingIds = List.of();

    @Inject
    private GtlClaimService claimService;

    @Inject
    private GtlQueryService gtlQueryService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (!buffer.isReadable()) {
            throw new IllegalArgumentException("Missing GTL claim listing count");
        }

        int count = buffer.readUnsignedByte();
        if (count < 1 || count > MAX_LISTINGS) {
            throw new IllegalArgumentException("Invalid GTL claim listing count: " + count);
        }
        int expectedPayloadSize = count * Long.BYTES;
        if (buffer.readableBytes() != expectedPayloadSize) {
            throw new IllegalArgumentException(
                    "Unexpected GTL claim payload size: " + buffer.readableBytes()
                            + ", expected=" + expectedPayloadSize);
        }

        List<Long> decodedListingIds = new ArrayList<>(count);
        Set<Long> uniqueListingIds = new HashSet<>(count);
        for (int index = 0; index < count; index++) {
            long listingId = buffer.readLongLE();
            if (listingId <= 0 || !uniqueListingIds.add(listingId)) {
                throw new IllegalArgumentException(
                        "Invalid or duplicate GTL claim listing ID: " + listingId);
            }
            decodedListingIds.add(listingId);
        }
        listingIds = List.copyOf(decodedListingIds);
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager =
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("Ignoring GTL claim request without a loaded character");
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }

        long sellerId = characterManager.getCharacterData()
                .getPlayerEntity().getEntityGameId();
        GtlClaimResult result = claimService.claimGtlListings(sellerId, listingIds);
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
        if (refresh == null) {
            session.send(new SendUpdatePlayerInfo(moneyUpdate));
            return;
        }
        session.send(new SendUpdatePlayerInfo(moneyUpdate), refresh);
    }
}
