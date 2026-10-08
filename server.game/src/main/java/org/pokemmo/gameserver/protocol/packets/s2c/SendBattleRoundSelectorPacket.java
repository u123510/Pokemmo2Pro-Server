package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.util.HashMap;
import java.util.Map;
public class SendBattleRoundSelectorPacket extends OutgoingPacket {
    private short fightRoundAmount;
    private boolean hasSelectMap;
    private Map<Byte,Byte> selectMap = new HashMap<>(1);
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        //测试代码
        selectMap.put((byte)0,(byte)0);

        buffer.writeShortLE(fightRoundAmount);
        buffer.writeBoolean(hasSelectMap);
        if(hasSelectMap){
            buffer.writeByte(selectMap.size());
            for (Map.Entry<Byte,Byte> entry : selectMap.entrySet()) {
                buffer.writeByte(entry.getKey());
                buffer.writeByte(entry.getValue());
            }
        }
    }
    public SendBattleRoundSelectorPacket(short fightRoundAmount) {
        this.fightRoundAmount = fightRoundAmount;
    }
    public void setHasSelectMap(short fightRoundAmount,Map<Byte,Byte> selectMap) {
        this.fightRoundAmount = fightRoundAmount;
        this.hasSelectMap = true;
        this.selectMap = selectMap;
    }
}
