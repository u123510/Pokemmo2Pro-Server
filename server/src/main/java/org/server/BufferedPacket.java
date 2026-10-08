package org.server;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HexFormat;

@Getter
@RequiredArgsConstructor
public class BufferedPacket {
  private static final HexFormat HEX = HexFormat.ofDelimiter(" ");

  private final byte opcode;
  private final byte[] data;

  public int getUnsignedOpcode() {
    return Byte.toUnsignedInt(opcode);
  }

  public int getWireLength() {
    return data.length + 1;
  }

  public String getOpcodeDescription() {
    return formatOpcode(opcode);
  }

  public String getPayloadHex() {
    return HEX.formatHex(data);
  }

  public String getWireHex() {
    String opcodeHex = HEX.formatHex(new byte[]{opcode});
    return data.length == 0 ? opcodeHex : opcodeHex + " " + getPayloadHex();
  }

  public static String formatOpcode(byte opcode) {
    int unsignedOpcode = Byte.toUnsignedInt(opcode);
    return String.format("0x%02X (%d)", unsignedOpcode, unsignedOpcode);
  }

  @Override
  public String toString() {
    return "BufferedPacket{" +
        "opcode=" + getOpcodeDescription() +
        ", payloadLength=" + data.length +
        ", wireLength=" + getWireLength() +
        ", payloadHex='" + getPayloadHex() + '\'' +
        '}';
  }
}
