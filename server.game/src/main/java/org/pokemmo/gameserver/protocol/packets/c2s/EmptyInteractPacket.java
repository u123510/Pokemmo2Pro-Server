package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.gameserver.game.interact.SceneInteractionService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

/** Receives the no-payload scene A-button fallback, not a PC-specific request. */
public final class EmptyInteractPacket extends IncomingPacket {
    private final SceneInteractionService sceneInteractionService;

    @Inject
    public EmptyInteractPacket(SceneInteractionService sceneInteractionService) {
        this.sceneInteractionService = sceneInteractionService;
    }

    @Override
    public boolean isLongRunning() {
        return true;
    }

    @Override
    public void decode(ByteBufEx buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("场景交互请求不应携带数据");
        }
    }

    @Override
    public void handle(Session session) {
        sceneInteractionService.handleEmptyInteraction(session);
    }
}
