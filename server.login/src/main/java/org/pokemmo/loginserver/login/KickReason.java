package org.pokemmo.loginserver.login;

import lombok.Getter;

@Getter
public class KickReason {
    private boolean hasReason;
    private LoginKickType kickType;
    private int banBeginTimeStamp;
    private int banFinishTimeStamp;
    private String banDescribe;
    public KickReason(LoginKickType kickType, int banBeginTimeStamp, int banFinishTimeStamp, String banDescribe) {
        this.hasReason = true;
        this.kickType = kickType;
        this.banBeginTimeStamp = banBeginTimeStamp;
        this.banFinishTimeStamp = banFinishTimeStamp;
        this.banDescribe = banDescribe;
    }
}
