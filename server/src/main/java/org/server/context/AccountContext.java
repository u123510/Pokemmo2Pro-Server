package org.server.context;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AccountContext {
    private byte gameSeverNodeId;
    private String serverName;
    private int port;
    private long reconnectCharacterId;
}
