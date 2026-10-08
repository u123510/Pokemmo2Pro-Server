package org.server.protocol.tls.packets.c2s.outgoing;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.server.util.ECUtil;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

@RequiredArgsConstructor
public class SendUpdateClientECPublicKeyPacket extends OutgoingPacket {
    private final PublicKey connectionKeyPublic;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        assert connectionKeyPublic instanceof ECPublicKey;
        byte[] uncompressedPoint = ECUtil.PublicKeyToUncompressedPoint((ECPublicKey) connectionKeyPublic);
        buffer.writeShortLE(uncompressedPoint.length);
        buffer.writeBytes(uncompressedPoint);
    }
}
