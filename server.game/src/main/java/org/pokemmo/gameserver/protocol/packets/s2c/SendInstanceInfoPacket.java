package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.instance.GameInstance;
import lombok.RequiredArgsConstructor;


import java.util.List;
@RequiredArgsConstructor
public class SendInstanceInfoPacket extends OutgoingPacket {
    private final List<GameInstance> instanceInfos;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(instanceInfos.size());
        for (GameInstance instanceInfo : instanceInfos) {
            Codecs.INSTANCE_INFO_CODEC.encode(buffer, instanceInfo);
        }
    }
}