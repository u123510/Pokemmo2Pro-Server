package org.pokemmo.loginserver.protocol.packets.s2c;

import com.github.maltalex.ineter.base.IPv4Address;
import lombok.RequiredArgsConstructor;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;
import org.pokemmo.loginserver.login.KickReason;
@RequiredArgsConstructor
public class SendLoginKickPacket  extends OutgoingPacket {
    private final KickReason kickReason;
    @Override
    public void encode(ByteBufEx buffer){
        buffer.writeBoolean(kickReason.isHasReason());
        if(kickReason.isHasReason()){
            buffer.writeByte(kickReason.getKickType().getType());
           switch (kickReason.getKickType()){
               case ACCOUNT:
                   buffer.writeIntLE(0);//unuse
                   buffer.writeIntLE(0);//unuse
                   buffer.writeIntLE(kickReason.getBanBeginTimeStamp());
                   buffer.writeIntLE(kickReason.getBanFinishTimeStamp());
                   buffer.writeUtf16LE(kickReason.getBanDescribe());
                   buffer.writeUtf16LE("");
                   buffer.writeBoolean(false);//unuse
                   break;
               case IP_ADDRESS:
                   buffer.writeIntLE(0);//unuse
                   buffer.writeIpLE( new IPv4Address(68755741));//ban ip范围
                   buffer.writeIpLE( new IPv4Address(68755791));
                   buffer.writeIntLE(0);//unuse
                   buffer.writeIntLE(0);//unuse
                   buffer.writeUtf16LE("");
                   buffer.writeBoolean(false);//unuse
                   break;
           }
        }
    }
}
