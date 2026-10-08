package org.pokemmo.loginserver.protocol.packets.s2c;

import com.github.maltalex.ineter.base.IPAddress;
import lombok.RequiredArgsConstructor;
import org.pokemmo.loginserver.service.LoginService;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.server.node.JoinableSeverData;

import java.util.List;

@RequiredArgsConstructor
public class SendReconnectPacket extends OutgoingPacket {
    private final long reconnectionCharacterId;
    private final byte[] reconnectGameSessionKey;
    private final byte gameNodeId;
    private final String gameServerName;
    private final IPAddress localAddress;
    private final String localHostname;
    private final int gameServerPort;
    private final short gameServerCurrentPlayerAmount;
    private final short gameServerMaxPlayerAmount;
    private final boolean isServerOpen;
    private final List<JoinableSeverData> nodeServers;
    @Override
    public void encode(ByteBufEx buffer){
        buffer.writeLongLE(reconnectionCharacterId);
        buffer.writeByte(reconnectGameSessionKey.length);
        buffer.writeBytes(reconnectGameSessionKey);
        buffer.writeByte(gameNodeId);
        buffer.writeUtf16LE(gameServerName);
        buffer.writeIpLE(localAddress);
        buffer.writeUtf16LE(localHostname);
        buffer.writeIntLE(gameServerPort);
        buffer.writeShortLE(gameServerCurrentPlayerAmount);
        buffer.writeShortLE(gameServerMaxPlayerAmount);
        buffer.writeBoolean(isServerOpen);
        buffer.writeByte(nodeServers.size());
        for (JoinableSeverData server : nodeServers) {
            buffer.writeByte(0); // unused
            buffer.writeIpLE(server.getAddress4());
            buffer.writeIpLE(server.getAddress6());
            buffer.writeShortLE(server.getPort());
            buffer.writeByte(server.getServerWeight());
        }
    }
}
