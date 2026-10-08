package org.pokemmo.gameserver.protocol.packets.c2s;

import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

public class OtherSettingUpdatePacket extends IncomingPacket {
    private int  otherSettingValue;
    private boolean isAutoRefuseBattleRequestFromOther;
    private boolean isAutoRefuseBattleRequestFromFriend;
    private boolean isAutoRefuseBattleRequestFromUnion;
    private boolean isAutoRefuseFriendRequestFromOther;
    private boolean isAutoRefuseFriendRequestFromUnion;
    private boolean isAutoRefuseTradeRequestFromOther;
    private boolean isAutoRefuseTradeRequestFromFriend;
    private boolean isAutoRefuseTradeRequestFromUnion;
    private boolean isAutoRefuseUnionRequestFromOther;
    private boolean isAutoRefuseUnionRequestFromFriend;
    private boolean isAutoRefuseTeamRequestFromOther;
    private boolean isAutoRefuseTeamRequestFromFriend;
    private boolean isAutoRefuseTeamRequestFromUnion;
    private boolean isAutoRefuseWhisperFromOther;
    private boolean isAutoRefuseWhisperFromFriend;
    private boolean isAutoRefuseWhisperFromUnion;
    @Override
    public void decode(ByteBufEx buffer) {
        otherSettingValue = buffer.readIntLE();
        isAutoRefuseBattleRequestFromOther = (otherSettingValue&1) != 0;
        isAutoRefuseBattleRequestFromFriend = (otherSettingValue&2) != 0;
        isAutoRefuseBattleRequestFromUnion = (otherSettingValue&4) != 0;
        isAutoRefuseFriendRequestFromOther = (otherSettingValue&8) != 0;
        isAutoRefuseFriendRequestFromUnion = (otherSettingValue&32) != 0;
        isAutoRefuseTradeRequestFromOther = (otherSettingValue&64) != 0;
        isAutoRefuseTradeRequestFromFriend = (otherSettingValue&128) != 0;
        isAutoRefuseTradeRequestFromUnion = (otherSettingValue&256) != 0;
        isAutoRefuseUnionRequestFromOther = (otherSettingValue&512) != 0;
        isAutoRefuseUnionRequestFromFriend = (otherSettingValue&1024) != 0;
        isAutoRefuseTeamRequestFromOther = (otherSettingValue&4096) != 0;
        isAutoRefuseTeamRequestFromFriend = (otherSettingValue&8192) != 0;
        isAutoRefuseTeamRequestFromUnion = (otherSettingValue&16384) != 0;
        isAutoRefuseWhisperFromOther = (otherSettingValue&32768) != 0;
        isAutoRefuseWhisperFromFriend = (otherSettingValue&65536) != 0;
        isAutoRefuseWhisperFromUnion = (otherSettingValue&131072) != 0;
    }

    @Override
    public void handle(Session session) throws Exception {

    }
}
