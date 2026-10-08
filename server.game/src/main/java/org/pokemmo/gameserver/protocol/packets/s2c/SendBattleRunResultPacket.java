package org.pokemmo.gameserver.protocol.packets.s2c;

import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.game.battle.BattleRunResultType;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
@RequiredArgsConstructor
public class SendBattleRunResultPacket extends OutgoingPacket {
    private final byte selectorData;
    private final BattleRunResultType resultType;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeByte(selectorData);
        buffer.writeByte(resultType.getType());
    }
}
