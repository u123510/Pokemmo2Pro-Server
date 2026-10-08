package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.container.PokemonContainerType;

/** Starts the client's dedicated wild capture animation. */
public final class SendBattleCapturePacket extends OutgoingPacket {
    private static final byte SUCCESS_RESULT = 4;

    private final byte selectorData;
    private final short ballItemIndexId;
    private final byte result;
    private final PokemonContainerType container;
    private final short position;

    public SendBattleCapturePacket(byte selectorData, short ballItemIndexId, byte result,
                                   PokemonContainerType container, short position) {
        if (result != 0 && result != 1 && result != 3 && result != SUCCESS_RESULT) {
            throw new IllegalArgumentException("第五世代捕获结果只能是 0、1、3 或 4");
        }
        if (result == SUCCESS_RESULT && (container == null || position < 0)) {
            throw new IllegalArgumentException("捕获成功必须提供容器和槽位");
        }
        if (result != SUCCESS_RESULT && (container != null || position >= 0)) {
            throw new IllegalArgumentException("捕获失败不能提供容器和槽位");
        }
        this.selectorData = selectorData;
        this.ballItemIndexId = ballItemIndexId;
        this.result = result;
        this.container = container;
        this.position = position;
    }

    @Override
    public void encode(ByteBufEx buffer) {
        // Client sv_1 reads selector, ball item index, result, then the
        // container/position pair only for a successful capture.
        buffer.writeByte(selectorData);
        buffer.writeShortLE(ballItemIndexId);
        buffer.writeByte(result);
        if (result == SUCCESS_RESULT) {
            buffer.writeByte(container.getType());
            buffer.writeShortLE(position);
        }
    }
}
