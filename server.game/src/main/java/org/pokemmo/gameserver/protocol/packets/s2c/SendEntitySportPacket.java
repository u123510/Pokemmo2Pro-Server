package org.pokemmo.gameserver.protocol.packets.s2c;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.entity.SportType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SendEntitySportPacket extends OutgoingPacket {
    private final long characterId;
    private final boolean isNdsType;
    private final List<SportType> sport;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeLongLE(characterId);
        buffer.writeBoolean(isNdsType);
        buffer.writeByte(sport.size());
        for(int i = 0;i<sport.size();i++){
            buffer.writeByte(sport.get(i).getType());
        }
    }
}
