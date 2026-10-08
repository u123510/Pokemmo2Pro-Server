package org.pokemmo.gameserver.protocol.packets.s2c;

import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.BattleStringCodec;
import org.pokemmo.gameserver.game.string.BattleString;

import java.util.List;

public class SendBattleFinishPacket extends OutgoingPacket {
    private byte winnerFaction;
    private List<BattleString> battleStrings_0;
    private List<BattleString> battleStrings_1;
    private int rewardMoney;
    private int pickUpMoney;
    private boolean directCloseBattleWidget;
    private List<BattleString> battleStrings_2;
    private BattleStringCodec battleStringCodec = new BattleStringCodec();
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(winnerFaction);
        buffer.writeByte(battleStrings_0.size());
        for(BattleString battleString : battleStrings_0)
            battleStringCodec.encode(buffer, battleString);
        buffer.writeByte(battleStrings_1.size());
        for(BattleString battleString : battleStrings_1)
            battleStringCodec.encode(buffer, battleString);
        buffer.writeIntLE(rewardMoney);
        buffer.writeIntLE(pickUpMoney);
        buffer.writeBoolean(directCloseBattleWidget);
        buffer.writeByte(battleStrings_2.size());
        for(BattleString battleString : battleStrings_2)
            battleStringCodec.encode(buffer, battleString);

        /*buffer.writeByte(0);//unk
        buffer.writeByte(1);//size
        buffer.writeByte(-1);//type
        buffer.writeByte(1);//size
        buffer.writeByte(-1);//type
        buffer.writeIntLE(0);//unk1
        buffer.writeIntLE(0);//unk2
        buffer.writeByte(1);//size

        buffer.writeByte(0);//type
        buffer.writeIntLE(6063);//stringId
        buffer.writeByte(1);//size
        buffer.writeByte(0);//stringType
        buffer.writeByte(0);//stringGroup
        buffer.writeByte(1);//stringArraySize
        buffer.writeShortLE(5243);//LocalStringIndex*/
    }
    public SendBattleFinishPacket(int winnerFaction, List<BattleString> battleStrings_0, List<BattleString> battleStrings_1, int rewardMoney, int pickUpMoney, boolean directCloseBattleWidget, List<BattleString> battleStrings_2) {
        this.winnerFaction = (byte) winnerFaction;
        this.battleStrings_0 = battleStrings_0;
        this.battleStrings_1 = battleStrings_1;
        this.rewardMoney = rewardMoney;
        this.pickUpMoney = pickUpMoney;
        this.directCloseBattleWidget = directCloseBattleWidget;
        this.battleStrings_2 = battleStrings_2;
    }
}
