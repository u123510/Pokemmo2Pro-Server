package org.server.node;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
@Getter
public class ServerNode {
    private final byte id;
    private final String name;
    private final int port;
    private final boolean isJoinAble;
}
