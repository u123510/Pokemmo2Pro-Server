package org.server;

import com.google.inject.Injector;
import org.server.bytes.ByteBufEx;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class Protocol {
  private final Map<DataFlow, Map<Byte, Class<? extends Packet>>> packets = new HashMap<>();
  private final Map<DataFlow, Map<Class<? extends Packet>, Byte>> opcodes = new HashMap<>();
  @Getter
  private final byte hashSize;
  @Getter
  private final boolean isAsync;
  @Getter
  private final boolean isCompressed;
  private final Injector injector;

  public Protocol(int hashSize, boolean isAsync, boolean isCompressed, Injector injector) {
    this.hashSize = (byte) hashSize;
    this.isAsync = isAsync;
    this.isCompressed = isCompressed;
    this.injector = injector;
  }

  public void registerPacket(DataFlow flow, byte opcode, Class<? extends Packet> packet) {
    packets.computeIfAbsent(flow, df -> new HashMap<>()).put(opcode, packet);
    opcodes.computeIfAbsent(flow, df -> new HashMap<>()).put(packet, opcode);
  }

  /**
   * Returns the registered packet type for diagnostics without instantiating it.
   */
  public String getRegisteredPacketName(BufferedPacket msg, DataFlow flow) {
    Map<Byte, Class<? extends Packet>> flowPackets = packets.get(flow);
    Class<? extends Packet> packetClass = flowPackets == null ? null : flowPackets.get(msg.getOpcode());
    return packetClass == null ? "<unregistered>" : packetClass.getName();
  }

  public boolean isSensitiveForLogging(BufferedPacket msg, DataFlow flow) {
    Map<Byte, Class<? extends Packet>> flowPackets = packets.get(flow);
    Class<? extends Packet> packetClass = flowPackets == null ? null : flowPackets.get(msg.getOpcode());
    return packetClass != null && Packet.containsSensitiveFields(packetClass);
  }

  public Packet decode(BufferedPacket msg, DataFlow flow) {
    byte opcode = msg.getOpcode();
    byte[] data = msg.getData();

    if (packets.containsKey(flow) && packets.get(flow).containsKey(opcode)) {
      Class<? extends Packet> packetCls = packets.get(flow).get(opcode);
      if (packetCls == null) {
        throw new IllegalStateException("No packet found for opcode");
      }

      ByteBufEx buffer = new ByteBufEx(data);
      try {
        Packet packet = injector.getInstance(packetCls);
        packet.decode(buffer);
        if (buffer.isReadable()) {
          throw new IllegalStateException("封包没有读取完全: consumed=" + buffer.readerIndex()
              + ", remaining=" + buffer.readableBytes());
        }

        return packet;
      } catch (Exception e) {
        log.debug("封包解析失败: flow={}, opcode={}, packet={}, payloadLength={}, consumed={}, remaining={}",
            flow,
            BufferedPacket.formatOpcode(opcode),
            packetCls.getName(),
            data.length,
            buffer.readerIndex(),
            buffer.readableBytes(),
            e);
        throw new IllegalStateException("无法解析封包: flow=" + flow
            + ", opcode=" + BufferedPacket.formatOpcode(opcode)
            + ", packet=" + packetCls.getName()
            + ", payloadLength=" + data.length
            + ", consumed=" + buffer.readerIndex()
            + ", remaining=" + buffer.readableBytes(), e);
      }
    }

    throw new IllegalStateException("无法找到封包来自 " + flow
        + " 封包id " + BufferedPacket.formatOpcode(opcode)
        + ", payloadLength=" + data.length);
  }

  public BufferedPacket encode(Packet packet, DataFlow flow) throws Exception {
    Byte opcode = opcodes.computeIfAbsent(flow, df -> new HashMap<>()).get(packet.getClass());
    if (opcode == null) {
      throw new IllegalStateException("No opcode found for packet " + packet.getClass().getSimpleName());
    }
    ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
    packet.encode(buffer);
    return new BufferedPacket(opcode, Arrays.copyOfRange(buffer.array(), buffer.readerIndex(), buffer.writerIndex()));
  }
}
