package org.pokemmo.gameserver.game.bin;

import lombok.Getter;
import lombok.Setter;
import java.io.FileInputStream;
import java.io.IOException;

@Setter @Getter
public class BinFileReader {
    public short readShortLE(FileInputStream fis) throws IOException {
        byte[] buffer = new byte[2];
        fis.read(buffer);
        return (short) ((buffer[1] << 8) | (buffer[0] & 0xFF));
    }
    public byte readByte(FileInputStream fis) throws IOException {
        return (byte) fis.read();
    }
    public int readIntLE(FileInputStream fis) throws IOException {
        byte[] buffer = new byte[4];
        fis.read(buffer);
        return ((buffer[3] << 24) | (buffer[2] << 16) | (buffer[1] << 8) | (buffer[0] & 0xFF));
    }
    public boolean readBoolean(FileInputStream fis) throws IOException {
        return readByte(fis) == 1;
    }
}
