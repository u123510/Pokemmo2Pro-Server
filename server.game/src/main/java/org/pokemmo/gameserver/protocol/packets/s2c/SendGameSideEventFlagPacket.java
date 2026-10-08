package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.events.GameEvent;
import lombok.RequiredArgsConstructor;
import java.util.List;
@RequiredArgsConstructor
public class SendGameSideEventFlagPacket extends OutgoingPacket{
    private final int regionId;
    private final List<GameEvent> events;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte((byte) regionId);
        buffer.writeShortLE(events.size());
        for (GameEvent event : events) {
            Codecs.GAMEEVENTFLAGS_CODEC.encode(buffer, event);
        }
    }
}