package org.server;

import org.server.handlers.FrameCodec;
import org.server.handlers.PacketCodec;
import org.server.util.TransportMethod;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class Server implements Runnable {
  private final EventLoopGroup acceptorGroup = createEventLoopGroup();
  private final EventLoopGroup clientGroup = createEventLoopGroup();
  private final Class<? extends ServerSocketChannel> channelClass = getServerSocketClass();
  private final int port;
  private final SessionInitializer sessionInitializer;
  private Thread thread;
  @Override
  public void run() {
    thread = Thread.currentThread();
    log.info("Starting server on port {}.", port);
    try {
      new ServerBootstrap()
          .group(acceptorGroup, clientGroup)
          .channel(channelClass)
          .childHandler(createChannelInitializer())
          .bind(port)
          .sync()
          .channel()
          .closeFuture()
          .sync();
    } catch (InterruptedException e) {
      log.warn("Server thread got interrupted. Shutting down.");
    } finally {
      acceptorGroup.shutdownGracefully();
      clientGroup.shutdownGracefully();
    }
    log.info("Server stopped.");
  }

  public void shutdown() {
    thread.interrupt();
  }

  private static EventLoopGroup createEventLoopGroup() {
    return switch (TransportMethod.getMethod()) {
      case NIO -> new NioEventLoopGroup();
      case EPOLL -> new EpollEventLoopGroup();
      case KQUEUE -> new KQueueEventLoopGroup();
    };
  }

  private static Class<? extends ServerSocketChannel> getServerSocketClass() {
    return switch (TransportMethod.getMethod()) {
      case NIO -> NioServerSocketChannel.class;
      case EPOLL -> EpollServerSocketChannel.class;
      case KQUEUE -> KQueueServerSocketChannel.class;
    };
  }

  private ChannelInitializer<SocketChannel> createChannelInitializer() {
    return new ChannelInitializer<>() {
      @Override
      protected void initChannel(@NonNull SocketChannel socketChannel) {
        socketChannel.pipeline()
          .addLast("timeout", new ReadTimeoutHandler(25, TimeUnit.MINUTES))
          .addLast("frame", new FrameCodec())
          .addLast("logger", new LoggingHandler("packets"))
          .addLast("packet", new PacketCodec())
                //为每次的连接通道分配一个会话
          .addLast("handler", sessionInitializer.initializeSession(Session.Side.SERVER));
      }
    };
  }
}
