package org.server;

import com.github.maltalex.ineter.base.IPAddress;
import org.server.handlers.CompressionHandler;
import org.server.handlers.EncryptionHandler;
import org.server.protocol.tls.TlsInfo;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class Session extends SimpleChannelInboundHandler<BufferedPacket> {
  private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();
  private enum State {
    UNENCRYPTED,
    ENCRYPTED
  }
  public enum Side {
    CLIENT,
    SERVER
  }
  public interface DisconnectListener {
    void onSessionDisconnected(Session session, ServerType serverType);
  }
  // Keep packets from one connection in arrival order. A cached pool can run
  // consecutive movement packets concurrently and let an older position win.
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  // Script/dialogue packets may sleep while waiting for a client-visible
  // animation. Keep them ordered, but do not block the normal packet queue.
  private final ExecutorService longRunningExecutor = Executors.newSingleThreadExecutor();
  private final long sessionId = NEXT_SESSION_ID.incrementAndGet();
  private final AtomicLong packetSequence = new AtomicLong();
  private final Protocol unencryptedProtocol;
  private static final List<DisconnectListener> disconnectListeners = new ArrayList<>();
  @Getter
  private final Protocol encryptedProtocol;
  private final Side side;
  private final ServerType serverType;
  private State state = State.UNENCRYPTED;
  @Getter
  private Channel channel;
  public boolean isActive() {
    return !executor.isShutdown()&&channel.isActive();
  }
  public static void registerDisconnectListener(DisconnectListener listener) {
    disconnectListeners.add(listener);
  }
  public static void unregisterDisconnectListener(DisconnectListener listener) {
    disconnectListeners.remove(listener);
  }
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (cause instanceof ReadTimeoutException) {
      log.warn("连接超时: session={}, channel={}, local={}, remote={}, state={}, serverType={}",
          sessionId, channelId(), localAddress(), remoteAddress(), state, serverType);
    } else {
      log.error("连接发生错误: session={}, channel={}, local={}, remote={}, state={}, serverType={}",
          sessionId, channelId(), localAddress(), remoteAddress(), state, serverType, cause);
    }

    // TODO: let the client know that the connection is being closed
    // PokeMMO sometimes doesnt handle disconnects properly and just hangs

    this.close();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    this.channel = ctx.channel();
    log.trace("连接已激活: session={}, channel={}, local={}, remote={}, side={}, serverType={}, state={}",
        sessionId, channelId(), localAddress(), remoteAddress(), side, serverType, state);
  }

  //读取通道中的封包
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BufferedPacket msg) {
    Protocol protocol = getProtocol();
    DataFlow flow = inboundFlow();
    long sequence = packetSequence.incrementAndGet();
    long decodeStartedAt = System.nanoTime();
    String registeredPacket = protocol.getRegisteredPacketName(msg, flow);
    String rawPacket = protocol.isSensitiveForLogging(msg, flow) ? "<redacted: sensitive packet>" : msg.getWireHex();
    Packet packet;
    try {
      packet = protocol.decode(msg, flow);
    } catch (RuntimeException e) {
      log.error("收包解析失败: session={}, channel={}, packet={}, flow={}, protocol={}, opcode={}, registeredPacket={}, "
              + "payloadBytes={}, packetBytes={}, packetHex={}",
          sessionId, channelId(), sequence, flow, protocol.getClass().getSimpleName(), msg.getOpcodeDescription(),
          registeredPacket, msg.getData().length, msg.getWireLength(), rawPacket, e);
      ctx.close();
      return;
    }

    long decodeMicros = elapsedMicros(decodeStartedAt);
    if (log.isTraceEnabled()) {
      log.trace("收包已解码: session={}, channel={}, packet={}, flow={}, protocol={}, opcode={}, packetType={}, "
              + "payloadBytes={}, packetBytes={}, decodeMicros={}, packetHex={}, fields={}",
          sessionId, channelId(), sequence, flow, protocol.getClass().getSimpleName(), msg.getOpcodeDescription(),
          packet.getClass().getName(), msg.getData().length, msg.getWireLength(), decodeMicros,
          packet.containsSensitiveData() ? "<redacted: sensitive packet>" : msg.getWireHex(),
          packet.debugDescription());
    }

    if (getProtocol().isAsync()) {
      ExecutorService packetExecutor = packet.isLongRunning() ? longRunningExecutor : executor;
      String queueName = packet.isLongRunning() ? "long-running" : "normal";
      if (log.isTraceEnabled()) {
        log.trace("收包已入队: session={}, channel={}, packet={}, queue={}, packetType={}",
            sessionId, channelId(), sequence, queueName, packet.getClass().getName());
      }
      packetExecutor.execute(() -> handleIncomingPacket(ctx, packet, flow, msg, sequence, queueName, decodeStartedAt));
    } else {
      handleIncomingPacket(ctx, packet, flow, msg, sequence, "event-loop", decodeStartedAt);
    }
  }
  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    executor.shutdownNow();
    longRunningExecutor.shutdownNow();
    log.trace("连接已断开: session={}, channel={}, local={}, remote={}, state={}, serverType={}",
        sessionId, channelId(), localAddress(), remoteAddress(), state, serverType);
    for(DisconnectListener listener : disconnectListeners){
      listener.onSessionDisconnected(this,serverType);
    }
    super.channelUnregistered(ctx);
  }

  public synchronized void send(Packet... packets) {
    Protocol protocol = getProtocol();
    DataFlow flow = outboundFlow();
    for (Packet packet : packets) {
      long sequence = packetSequence.incrementAndGet();
      long encodeStartedAt = System.nanoTime();
      BufferedPacket bufferedPacket;
      try {
        if( side == Side.CLIENT){
          log.error("客户端侧 Session 发送封包: session={}, channel={}, packet={}, packetType={}",
              sessionId, channelId(), sequence, packet.getClass().getName());
        }
        //加密封包
        bufferedPacket = protocol.encode(packet, flow);
      } catch (Exception e) {
        log.error("发包编码失败: session={}, channel={}, packet={}, flow={}, protocol={}, packetType={}",
            sessionId, channelId(), sequence, flow, protocol.getClass().getSimpleName(), packet.getClass().getName(), e);
        return;
      }
      if (log.isTraceEnabled()) {
        log.trace("发包已编码: session={}, channel={}, packet={}, flow={}, protocol={}, opcode={}, packetType={}, "
                + "payloadBytes={}, packetBytes={}, encodeMicros={}, packetHex={}, fields={}",
            sessionId, channelId(), sequence, flow, protocol.getClass().getSimpleName(),
            bufferedPacket.getOpcodeDescription(), packet.getClass().getName(), bufferedPacket.getData().length,
            bufferedPacket.getWireLength(), elapsedMicros(encodeStartedAt),
            packet.containsSensitiveData() ? "<redacted: sensitive packet>" : bufferedPacket.getWireHex(),
            packet.debugDescription());
      }
      channel.write(bufferedPacket);
    }
    channel.flush();
  }

  public void enableEncryption(TlsInfo tlsInfo) {
    if (channel.pipeline().get("encryption") != null) {
      throw new IllegalStateException("连接已经加密了");
    }

    state = State.ENCRYPTED;
    channel.pipeline()
        .addAfter("frame", "encryption", new EncryptionHandler(tlsInfo, side));

    log.trace("连接已启用加密: session={}, channel={}, local={}, remote={}, side={}, serverType={}, protocol={}, hashSize={}",
        sessionId, channelId(), localAddress(), remoteAddress(), side, serverType,
        getProtocol().getClass().getSimpleName(), Byte.toUnsignedInt(getProtocol().getHashSize()));
    // find a better place for this
    if (getProtocol().isCompressed()) {
      enableCompression();
    }
  }

  public Protocol getProtocol() {
    return state == State.ENCRYPTED ? encryptedProtocol : unencryptedProtocol;
  }

  public <T> Attribute<T> attr(AttributeKey<T> key) {
    return channel.attr(key);
  }

  public IPAddress getRemoteAddress() {
    InetSocketAddress inetSocketAddress = (InetSocketAddress) channel.remoteAddress();
    return IPAddress.of(inetSocketAddress.getAddress());
  }

  public void close() {
    executor.shutdownNow();
    longRunningExecutor.shutdownNow();
    channel.close();
  }

  public void enableCompression() {
    if (channel.pipeline().get("compression") != null) {
      throw new IllegalStateException("Connection is already compressed.");
    }
    channel.pipeline()
        .addAfter("packet", "compression", new CompressionHandler());
    log.trace("连接已启用压缩: session={}, channel={}, local={}, remote={}, protocol={}",
        sessionId, channelId(), localAddress(), remoteAddress(), getProtocol().getClass().getSimpleName());
  }

  private void handleIncomingPacket(
      ChannelHandlerContext ctx,
      Packet packet,
      DataFlow flow,
      BufferedPacket bufferedPacket,
      long sequence,
      String queueName,
      long decodedAt) {
    long handleStartedAt = System.nanoTime();
    if (log.isTraceEnabled()) {
      log.trace("收包开始处理: session={}, channel={}, packet={}, queue={}, opcode={}, packetType={}, queueWaitMicros={}",
          sessionId, channelId(), sequence, queueName, bufferedPacket.getOpcodeDescription(), packet.getClass().getName(),
          elapsedMicros(decodedAt));
    }
    try {
      packet.handle(this);
      if (log.isTraceEnabled()) {
        log.trace("收包处理完成: session={}, channel={}, packet={}, flow={}, queue={}, opcode={}, packetType={}, handleMicros={}",
            sessionId, channelId(), sequence, flow, queueName, bufferedPacket.getOpcodeDescription(),
            packet.getClass().getName(), elapsedMicros(handleStartedAt));
      }
    } catch (Exception e) {
      log.error("收包处理失败: session={}, channel={}, packet={}, flow={}, queue={}, opcode={}, packetType={}, "
              + "handleMicros={}, fields={}",
          sessionId, channelId(), sequence, flow, queueName, bufferedPacket.getOpcodeDescription(),
          packet.getClass().getName(), elapsedMicros(handleStartedAt), packet.debugDescription(), e);
      if (isRecoverableConnectionFailure(e)) {
        log.warn("数据库连接暂时不可用，保留客户端连接并等待连接池恢复: session={}, channel={}, packet={}, packetType={}",
            sessionId, channelId(), sequence, packet.getClass().getName());
        return;
      }
      ctx.close();
    }
  }

  private DataFlow inboundFlow() {
    return side == Side.CLIENT ? DataFlow.SERVER_TO_CLIENT : DataFlow.CLIENT_TO_SERVER;
  }

  private DataFlow outboundFlow() {
    return side == Side.CLIENT ? DataFlow.CLIENT_TO_SERVER : DataFlow.SERVER_TO_CLIENT;
  }

  private static long elapsedMicros(long startedAt) {
    return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt);
  }

  public static boolean isRecoverableConnectionFailure(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof SocketException) {
        return true;
      }
      if (current instanceof SQLException sqlException) {
        String sqlState = sqlException.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
          return true;
        }
      }
      String message = current.getMessage();
      if (message != null) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("connection reset by peer")
            || normalized.contains("this connection has been closed")
            || normalized.contains("connection is closed")
            || normalized.contains("i/o error occurred while sending to the backend")
            || normalized.contains("broken pipe")) {
          return true;
        }
      }
    }
    return false;
  }

  private String channelId() {
    return channel == null ? "<unbound>" : channel.id().asShortText();
  }

  private Object localAddress() {
    return channel == null ? "<unbound>" : channel.localAddress();
  }

  private Object remoteAddress() {
    return channel == null ? "<unbound>" : channel.remoteAddress();
  }
}
