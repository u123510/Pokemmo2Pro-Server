package org.server.handlers;

import org.server.BufferedPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

public class PacketCodec extends MessageToMessageCodec<ByteBuf, BufferedPacket> {
  @Override
  protected void encode(ChannelHandlerContext ctx, BufferedPacket msg, List<Object> out) throws Exception {
    ByteBuf buffer = Unpooled.buffer();
    buffer.retain();
    buffer.writeByte(msg.getOpcode());
    try {
      buffer.writeBytes(msg.getData());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write buffered packet to data stream.", e);
    }
    out.add(buffer);
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
    int opcode = msg.readUnsignedByte();
    byte[] data = new byte[msg.readableBytes()];
    msg.readBytes(data);
    out.add(new BufferedPacket((byte) opcode, data));
  }
}
