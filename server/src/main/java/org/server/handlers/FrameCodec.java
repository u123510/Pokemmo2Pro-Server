package org.server.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * FrameCodec is a {@link ChannelDuplexHandler} that that adds a 2-byte length field to the beginning
 * of each packet. The length field is a little-endian unsigned short that represents the length of the
 * packet in bytes, including the length field itself.
 */
@Slf4j
public class FrameCodec extends ByteToMessageCodec<ByteBuf> {

  @Override
  protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
    if (msg.readableBytes() > 0xFFFF) {
      log.error("Packet too large: {}", msg.readableBytes());
      throw new IllegalStateException("Packet too large");
    }

    out.writeShortLE((msg.readableBytes() + 2) & 0xFFFF);
    out.writeBytes(msg);
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (in.readableBytes() < 2) {
      return;
    }

    while (in.isReadable(2)) {
      in.markReaderIndex();
      int length = in.readUnsignedShortLE() - 2;
      if (!in.isReadable(length)) {
        in.resetReaderIndex(); // not enough data
        break;
      }

      out.add(in.readBytes(length));
    }
  }
}
