package org.pokemmo.gameserver.util;

import java.io.FileInputStream;
import java.io.IOException;

public class BinFileReader {
    public static byte[] readEntireFile(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] data = new byte[fis.available()];
            fis.read(data);
            return data;
        }
    }
}
