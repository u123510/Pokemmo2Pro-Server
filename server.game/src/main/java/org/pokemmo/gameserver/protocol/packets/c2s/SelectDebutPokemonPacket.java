package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class SelectDebutPokemonPacket extends IncomingPacket {
    private long[] pokemonIds;
    @Override
    public void decode(ByteBufEx buffer) {
        byte length = buffer.readByte();
        pokemonIds = new long[length];
        for (int i = 0; i < length; i++) {
            pokemonIds[i] = buffer.readLongLE();
        }
    }
    @Override
    public void handle(Session session) throws Exception {
        BattleManager battleManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getBattleManager();
        if (battleManager == null || battleManager.isSpectator(session)) {
            return;
        }
        battleManager.handlePreviewBattlePokemonDebut(session,false,false);

    }
}
