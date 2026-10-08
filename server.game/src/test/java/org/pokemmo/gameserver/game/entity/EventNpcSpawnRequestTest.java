package org.pokemmo.gameserver.game.entity;

import java.util.Arrays;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddGameEntityPacket;
import org.server.bytes.ByteBufEx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventNpcSpawnRequestTest {
    @Test
    void parsesCapturedFourteenFieldsWithoutMixingSpawnnpcArgumentOrder() {
        EventNpcSpawnRequest request = EventNpcSpawnRequest.parse(captured());
        assertEquals(new NpcSpawnRequest(248, 0, 10, 0, 0, 0), request.npc());
        assertEquals(new NpcSpawnAppearance(0, false, 1.0f), request.appearance());
    }

    @Test
    void supportsKnownEventCategoriesAndVisualOptions() {
        for (int event = 0; event <= 6; event++) {
            String[] args = captured();
            args[0] = Integer.toString(event);
            args[10] = "true";
            args[12] = "2.0";
            EventNpcSpawnRequest request = EventNpcSpawnRequest.parse(args);
            assertEquals(event, request.appearance().eventId());
            assertTrue(request.appearance().sparkles());
            assertEquals(2.0f, request.appearance().spriteScale());
        }
    }

    @Test
    void rejectsUnsupportedSemanticsInsteadOfIgnoringThem() {
        assertRejected(6, "1", "脚本偏移");
        assertRejected(7, "1", "标志条件");
        assertRejected(8, "0", "标志条件");
        assertRejected(9, "true", "更新已有 NPC");
        assertRejected(11, "true", "忽略重复");
        String[] conditions = Arrays.copyOf(captured(), 16);
        conditions[13] = "1";
        conditions[14] = "0";
        conditions[15] = "1";
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> EventNpcSpawnRequest.parse(conditions)).getMessage().contains("附加事件条件"));
    }

    @Test
    void rejectsMalformedCountsTruncationTrailingArgumentsAndHugeNumbers() {
        assertThrows(IllegalArgumentException.class, () -> EventNpcSpawnRequest.parse(null));
        assertThrows(IllegalArgumentException.class, () -> EventNpcSpawnRequest.parse(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> EventNpcSpawnRequest.parse(Arrays.copyOf(captured(), 13)));
        assertThrows(IllegalArgumentException.class, () -> EventNpcSpawnRequest.parse(Arrays.copyOf(captured(), 15)));
        assertRejected(13, "-1", "条件数量");
        assertRejected(13, "1", "参数数量");
        assertRejected(13, "2147483647", "条件数量");
        assertRejected(13, "2147483648", "32 位");
        assertRejected(0, "256", "事件编号");
        assertRejected(0, "-1", "事件编号");
        assertRejected(1, "10001", "外观编号");
        assertRejected(2, "9", "外观地区");
        assertRejected(4, "5", "活动范围");
        assertRejected(7, "65536", "标志编号");
        assertRejected(1, "2.5", "32 位");
    }

    @Test
    void booleanAndScaleValidationNeverCoercesBadValues() {
        for (int flag : new int[]{9, 10, 11}) assertRejected(flag, "yes", "true 或 false");
        for (String scale : new String[]{"NaN", "Infinity", "-Infinity", "1e100", "0", "-1", "0.24", "4.01"}) {
            assertRejected(12, scale, "缩放");
        }
        assertRejected(12, "abc", "缩放");
    }

    @Test
    void visualFlagsReuseNativeNpcPacketWithoutSendingEventId() throws Exception {
        NpcEntity npc = new NpcEntity(7, true, "npc_100000", 1, 0, 0, 0, 0, 5, 3, 10, 248, 4, 3, 2);
        new NpcSpawnAppearance(0, true, 2.0f).applyTo(npc);
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new SendAddGameEntityPacket(npc).encode(buffer);
            assertEquals(30, buffer.readableBytes());
            buffer.skipBytes(24);
            assertEquals(8 | 2048 | 4096, buffer.readUnsignedShortLE());
            assertEquals(2.0f, buffer.readFloatLE());
            assertFalse(buffer.isReadable());
            NpcSpawnAppearance.DEFAULT.applyTo(npc);
            buffer.clear();
            new SendAddGameEntityPacket(npc).encode(buffer);
            assertEquals(26, buffer.readableBytes());
            buffer.skipBytes(24);
            assertEquals(8, buffer.readUnsignedShortLE());
        } finally {
            buffer.release();
        }
    }

    private static void assertRejected(int index, String value, String expectedMessage) {
        String[] args = captured();
        args[index] = value;
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> EventNpcSpawnRequest.parse(args)).getMessage().contains(expectedMessage));
    }

    private static String[] captured() {
        return "0 248 10 0 0 0 0 -1 -1 false false false 1.0 0".split(" ");
    }
}
