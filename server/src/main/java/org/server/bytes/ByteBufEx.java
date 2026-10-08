package org.server.bytes;

import com.github.maltalex.ineter.base.IPAddress;
import com.github.maltalex.ineter.base.IPv4Address;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Extended ByteBuf with additional pokemmo related methods.
 * @see ByteBufDelegator
 */
public class ByteBufEx extends ByteBufDelegator {

  public ByteBufEx(ByteBuf delegate) {
    super(delegate);
  }

  public ByteBufEx(byte[]... bytes) {
    super(Unpooled.wrappedBuffer(bytes));
  }

  public String toHexString() {
    markReaderIndex();
    StringBuilder sb = new StringBuilder();
    while (isReadable()) {
      sb.append(Integer.toHexString(readUnsignedByte()));
      if (isReadable()) {
        sb.append(" ");
      }
    }
    resetReaderIndex();
    return sb.toString();
  }

  public char readCharLE() {
    return (char) (readUnsignedByte() | (readUnsignedByte() << 8));
  }

  public void writeCharLE(char value) {
    writeByte(value & 0xFF);
    writeByte((value >> 8) & 0xFF);
  }

  public String readUtf16LE() {
    StringBuilder builder = new StringBuilder();
    char c;
    while ((c = readCharLE()) != 0) {
      builder.append(c);
    }
    return builder.toString();
  }

  public void writeUtf16LE(String value) {
    for (char c : value.toCharArray()) {
      writeCharLE(c);
    }
    writeCharLE((char) 0);
  }
  public void writeBuffer(ByteBufEx buffer) {
    writeBytes(buffer);
  }
  public void writeUtf8LE(String value) {
    for (char c : value.toCharArray()) {
      writeCharLE(c);
    }
  }

  public String readUtf16() {
    StringBuilder builder = new StringBuilder();
    char c;
    while ((c = readChar()) != 0) {
      builder.append(c);
    }
    return builder.toString();
  }

  public void writeUtf16(String value) {
    for (char c : value.toCharArray()) {
      writeChar(c);
    }
    writeChar((char) 0);
  }

  public void writeIpLE(IPAddress ipAddress) {
    if (ipAddress instanceof IPv4Address) {
      writeByte(4);
      writeIntLE(((IPv4Address) ipAddress).toInt());
    } else {
      writeByte(6);
      writeBytes(ipAddress.toArray());
    }
  }

  public byte[] readByteArray(int length) {
    byte[] bytes = new byte[length];
    readBytes(bytes);
    return bytes;
  }
}
