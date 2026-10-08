package org.pokemmo.gameserver.protocol.packets.s2c;
import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.server.node.JoinableSeverData;
import org.server.node.ServerNode;

import java.util.List;
@RequiredArgsConstructor
public class SendUpdateChatServerSessionKeyPacket extends OutgoingPacket{
    private final byte[] chatSessionKey;
    private final List<JoinableSeverData> nodeServers;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(chatSessionKey.length);
        buffer.writeBytes(chatSessionKey);
        buffer.writeByte(nodeServers.size());
        for (JoinableSeverData node : nodeServers) {
            buffer.writeByte(0); // unused
            buffer.writeIpLE(node.getAddress4());
            buffer.writeIpLE(node.getAddress6());
            buffer.writeShortLE(node.getPort());
            buffer.writeByte(node.getServerWeight());
        }
    }
}
