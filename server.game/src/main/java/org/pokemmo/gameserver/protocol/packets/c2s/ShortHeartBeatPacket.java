package org.pokemmo.gameserver.protocol.packets.c2s;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShortHeartBeatPacket;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class ShortHeartBeatPacket extends IncomingPacket {
    private boolean isHeartBeat;
    private long timeStamp;

    @Override
    public void decode(ByteBufEx buffer) {
        isHeartBeat = buffer.readBoolean();
        timeStamp= buffer.readLongLE();
    }
    @Override
    public void handle(Session session) throws Exception {
        log.trace("收到短心跳来自用户 {}. ", session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getAccountData().getAccountId());
        if(isHeartBeat){
            long currentTime = System.currentTimeMillis();
            if(timeStamp>currentTime + 5000){
                //说明本地的时间被加速了
                session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterHeartBeatThread().setClientTimeAccelerated(true);
                return;
            }
            session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterHeartBeatThread().setClinetHeartbeatTimeStamp(timeStamp);
        }
        else{
            //说明是ping指令
            session.send(new SendShortHeartBeatPacket(false));
        }
    }
}
