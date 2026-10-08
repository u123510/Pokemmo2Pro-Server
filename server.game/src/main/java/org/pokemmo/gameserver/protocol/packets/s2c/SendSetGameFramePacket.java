package org.pokemmo.gameserver.protocol.packets.s2c;

import org.pokemmo.gameserver.game.frame.FrameType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendSetGameFramePacket extends OutgoingPacket {
    private final FrameType frameType ;
    private final byte interactTimes;
    private final short[] FlashArray;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(frameType.getType());
        buffer.writeByte(interactTimes);
        buffer.writeByte(FlashArray.length);
        /*if(flashType == 4){
            FlashArray[0] = screenMaskType;
            FlashArray[1] = screenGradientTime;
        }*/
        for (short flash : FlashArray) {
            buffer.writeShortLE(flash);
        }
    }
}
