package org.server.util;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;

public enum TransportMethod {
  NIO, EPOLL, KQUEUE;

  public static TransportMethod getMethod() {
    if (isClassLoadable("io.netty.channel.epoll.Epoll") && Epoll.isAvailable()) {
      return EPOLL;
    } else if (isClassLoadable("io.netty.channel.kqueue.KQueue") && KQueue.isAvailable()) {
      return KQUEUE;
    } else {
      return NIO;
    }
  }

  private static boolean isClassLoadable(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
