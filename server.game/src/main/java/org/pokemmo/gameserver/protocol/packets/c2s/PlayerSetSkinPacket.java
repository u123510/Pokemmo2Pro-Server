package org.pokemmo.gameserver.protocol.packets.c2s;

import com.google.inject.Inject;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import static org.pokemmo.gameserver.codecs.Codecs.SKIN_CODEC_1;

public class PlayerSetSkinPacket extends IncomingPacket {
    private long playerId;
    private byte characterCustomizeType;
    private byte interactTimes;
    private byte selectedIndex;
    @Inject
    private GameServerService characterService;
    @Override
    public void decode(ByteBufEx buffer) {
        playerId = buffer.readLongLE();
        characterCustomizeType = buffer.readByte();
        interactTimes = buffer.readByte();
        SKIN_CODEC_1.decode(buffer, characterService.getCharacter(playerId));
        selectedIndex = buffer.readByte();
    }
    @Override
    public void handle(Session session) throws Exception {

    }
}
