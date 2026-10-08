package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendCharacterBattleStatus extends OutgoingPacket {
    private final long characterId;
    private final byte battleStatus;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {

    }
}
