package org.server.node;

import com.github.maltalex.ineter.base.IPAddress;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
@Getter
public class JoinableSeverData {
    private final IPAddress address4;
    private final IPAddress address6;
    private final int port;
    private final byte serverWeight;//服务器负载均衡权重
}
