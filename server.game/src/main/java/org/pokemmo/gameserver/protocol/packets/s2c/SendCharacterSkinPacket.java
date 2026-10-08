package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

@RequiredArgsConstructor
public class SendCharacterSkinPacket extends OutgoingPacket {
    private static final int SKIN_MASK = 0x3FF;
    private static final int COLOR_MASK = 0x3F;

    private final long characterId;
    private final SkinType skinType;
    private final short skin;
    private final short color;

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeLongLE(characterId);
        buffer.writeByte(skinType.getType());
        int skinValue = skin < 0 ? SKIN_MASK : skin;
        int colorValue = color < 0 ? COLOR_MASK : color;
        buffer.writeShortLE((short) ((skinValue & SKIN_MASK) | ((colorValue & COLOR_MASK) << 10)));
    }
}
