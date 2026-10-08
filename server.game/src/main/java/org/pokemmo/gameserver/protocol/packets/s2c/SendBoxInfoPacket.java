package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

public class SendBoxInfoPacket extends OutgoingPacket {
    private static final int POKEMON_PER_BOX = 60;
    private static final int MAX_ENCODED_BOX_AMOUNT = Byte.MAX_VALUE;

    private final byte[] boxIndexMap;

    public SendBoxInfoPacket() {
        this((short) 0);
    }

    public SendBoxInfoPacket(short pcBoxExpansionNumber) {
        int baseBoxAmount = PokemonContainerType.PC.getSize() / POKEMON_PER_BOX;
        int boxAmount = Math.min(MAX_ENCODED_BOX_AMOUNT,
                baseBoxAmount + Math.max(0, pcBoxExpansionNumber));
        boxIndexMap = new byte[boxAmount];
        for (int index = 0; index < boxAmount; index++) {
            boxIndexMap[index] = (byte) index;
        }
    }

    @Override
    public void encode(ByteBufEx buffer) {
        buffer.writeByte(1);
        buffer.writeByte(boxIndexMap.length);
        for (byte boxIndex : boxIndexMap) {
            buffer.writeByte(boxIndex);
        }
    }
}
