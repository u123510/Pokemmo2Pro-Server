package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.gtl.GtlActionResult;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGtlActionResultPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGTLPacket;
import org.pokemmo.gameserver.services.gtl.GtlCancellationService;
import org.pokemmo.gameserver.services.gtl.GtlListingCancelResult;
import org.pokemmo.gameserver.services.gtl.GtlQueryService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

@Slf4j
public class CancelGTLListingPacket extends IncomingPacket {
    private static final int PAYLOAD_SIZE = Long.BYTES;

    private long listingId;

    @Inject
    private GtlCancellationService cancellationService;

    @Inject
    private GtlQueryService gtlQueryService;

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.readableBytes() != PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Unexpected GTL cancellation payload size: " + buffer.readableBytes());
        }
        listingId = buffer.readLongLE();
    }

    @Override
    public void handle(Session session) throws Exception {
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (characterManager == null || characterManager.getCharacterData() == null
                || characterManager.getCharacterData().getPlayerEntity() == null) {
            log.warn("Ignoring GTL cancellation request without a loaded character");
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }
        if (listingId <= 0) {
            log.warn("Rejecting invalid GTL cancellation request: listingId={}", listingId);
            session.send(new SendGtlActionResultPacket(GtlActionResult.REJECTED));
            return;
        }

        long characterId = characterManager.getCharacterData().getPlayerEntity().getEntityGameId();
        GtlListingCancelResult result =
                cancellationService.cancelPokemonGtlListing(characterId, listingId);
        if (result.result() != GtlActionResult.SUCCESS || result.pokemon() == null) {
            session.send(new SendGtlActionResultPacket(result.result()));
            return;
        }

        SendGTLPacket refresh = RequestGTLPacket.createCurrentPageRefresh(
                session, gtlQueryService);
        if (refresh == null) {
            session.send(
                    new SendGtlActionResultPacket(result.result()),
                    new SendAddPokemonPacket(result.pokemon()));
            return;
        }
        session.send(
                new SendGtlActionResultPacket(result.result()),
                new SendAddPokemonPacket(result.pokemon()),
                refresh);
    }
}
