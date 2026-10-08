package org.server.util;

import org.jooq.postgres.extensions.types.Inet;

public class IpUtil {
    public static Inet getLocalIPv4Inet() {
        try {
            return Inet.inet(java.net.InetAddress.getByName("127.0.0.1"));
            //java.net.InetAddress inetAddress = java.net.InetAddress.getLocalHost();
            //return Inet.inet(inetAddress);
        } catch (Exception e) {
            try {
                return Inet.inet(java.net.InetAddress.getByName("127.0.0.1"));
            } catch (Exception ex) {
                throw new RuntimeException("无法获取IPv4地址", ex);
            }
        }
    }
    public static Inet getLocalIPv6Inet() {
        try {
            /*java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(java.net.InetAddress.getLocalHost().getHostName());
            for (java.net.InetAddress addr : addresses) {
                if (addr instanceof java.net.Inet6Address && !addr.isLinkLocalAddress()) {
                    return Inet.inet(addr);
                }
            }*/
            // 如果没有找到有效的IPv6地址，使用本地IPv6地址
            return Inet.inet(java.net.InetAddress.getByName("::1"));
        } catch (Exception e) {
            try {
                return Inet.inet(java.net.InetAddress.getByName("::1"));
            } catch (Exception ex) {
                throw new RuntimeException("无法获取IPv6地址", ex);
            }
        }
    }
}
