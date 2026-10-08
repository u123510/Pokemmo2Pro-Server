package org.pokemmo.gameserver.protocol.packets.s2c;
import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.codecs.Codecs;
import org.pokemmo.gameserver.game.string.GameMassageString;

@RequiredArgsConstructor
public class SendGameMessagePacket extends OutgoingPacket{
    private final GameMassageString gameMassageString;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        buffer.writeIntLE(gameMassageString.getLocalStringIndexId());
        byte realSize = (byte) gameMassageString.getLocalStringList().size();
        byte flag = realSize;
        if(gameMassageString.isBattleString())
            flag |= 64;
        if(gameMassageString.isGameChatString())
            flag |= 128;
        buffer.writeByte(flag);
        for(int i=0;i<realSize;i++) {
            Codecs.GAME_LOCAL_STRING_CODEC.encode(buffer, gameMassageString.getLocalStringList().get(i));
        }
       if(gameMassageString.isGameChatString()){
           buffer.writeByte(gameMassageString.getChatType().getType());
       }
    }
}
