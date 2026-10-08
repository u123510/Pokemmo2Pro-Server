package org.pokemmo.gameserver.protocol.packets.s2c;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.pokemmo.gameserver.services.GameServerService;
import org.server.OutgoingPacket;
import org.server.bytes.ByteBufEx;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
@RequiredArgsConstructor
public class SendLoadDexPacket extends OutgoingPacket {
    private final BitSet[] dexPokemonUnlockFlag;
    private List<Map<Short, Integer>> catchMasks = new ArrayList<>(4);
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        // 初始化数据
        for (int i = 0; i < 4; i++) {
            catchMasks.add(new HashMap<>());
        }
        // 写入数据块数量
        buffer.writeByte(4);
        for (int i = 0; i < 4; i++) {
            // 创建一个临时输出流来构建未压缩的数据
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            // 写入BitSet的字节数组
            byte[] bitsetBytes = dexPokemonUnlockFlag[i].toByteArray();
            dos.writeShort(bitsetBytes.length);
            dos.write(bitsetBytes);

            // 写入Map的大小
            dos.writeByte(catchMasks.get(i).size());
            // 写入Map的内容
            for (Map.Entry<Short, Integer> entry : catchMasks.get(i).entrySet()) {
                dos.writeInt(entry.getKey()); // 注意：这里可能需要调整顺序，根据客户端的读取顺序
                dos.writeShort(entry.getValue());
            }
            dos.close();
            // 获取未压缩的数据
            byte[] uncompressedData = baos.toByteArray();
            // 压缩数据
            byte[] compressedData = compress(uncompressedData);
            // 写入压缩数据的长度
            buffer.writeShortLE(compressedData.length);
            // 写入压缩后的数据
            buffer.writeBytes(compressedData);
        }
    }
    private static byte[] compress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        try {
            DeflaterOutputStream deflater = new DeflaterOutputStream(out);
            deflater.write(data);
            deflater.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
