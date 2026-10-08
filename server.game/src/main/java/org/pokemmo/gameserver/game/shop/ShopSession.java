package org.pokemmo.gameserver.game.shop;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;

/** Accessed under the owning character's InteractManager monitor. Never persisted across connections. */
public final class ShopSession {
    enum RequestState { NEXT, DUPLICATE, INVALID }

    record Reply(int status, String message) {
    }

    final long quoteId;
    final long catalogVersion;
    final ShopDefinition definition;
    final CharacterManager manager;
    final long ownerId;
    final long npcId;
    private final int channel;
    private final byte region;
    private final byte bank;
    private final byte map;
    private final short x;
    private final short y;
    private final byte z;
    private ShopRequest lastRequest;
    private Reply lastReply;

    ShopSession(long quoteId, long catalogVersion, ShopDefinition definition,
                CharacterManager manager, NpcEntity npc) {
        this.quoteId = quoteId;
        this.catalogVersion = catalogVersion;
        this.definition = definition;
        this.manager = manager;
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        ownerId = player.getEntityGameId();
        npcId = npc.getEntityGameId();
        channel = manager.getCharacterData().getChannel();
        region = player.getRegionIndexId();
        bank = player.getMapHeaderIdOrGbaMapGroupId();
        map = player.getGbaMapId();
        x = player.getX();
        y = player.getY();
        z = player.getZ();
    }

    boolean unchangedPosition() {
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        return ownerId == player.getEntityGameId() && channel == manager.getCharacterData().getChannel()
                && region == player.getRegionIndexId() && bank == player.getMapHeaderIdOrGbaMapGroupId()
                && map == player.getGbaMapId() && x == player.getX() && y == player.getY() && z == player.getZ();
    }

    RequestState classify(ShopRequest request) {
        if (lastRequest != null && lastRequest.equals(request)) {
            return RequestState.DUPLICATE;
        }
        long expected = lastRequest == null ? 1 : (long) lastRequest.requestId() + 1;
        return request.requestId() == expected ? RequestState.NEXT : RequestState.INVALID;
    }

    Reply lastReply() {
        return lastReply;
    }

    void remember(ShopRequest request, int status, String message) {
        lastRequest = request;
        lastReply = new Reply(status, message);
    }
}
