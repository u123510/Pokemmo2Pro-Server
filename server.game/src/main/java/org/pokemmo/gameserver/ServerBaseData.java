package org.pokemmo.gameserver;

import org.jooq.postgres.extensions.types.Inet;
import org.server.util.IpUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerBaseData {
    public static final int port = 7777;
    public static final Inet ipv4Address = IpUtil.getLocalIPv4Inet();
    public static final Inet Ipv6Address = IpUtil.getLocalIPv6Inet();
    public String localHostName = InetAddress.getLocalHost().getHostName();

    public ServerBaseData() throws UnknownHostException {

    }
}
