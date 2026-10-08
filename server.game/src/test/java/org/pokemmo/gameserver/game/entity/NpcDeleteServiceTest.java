package org.pokemmo.gameserver.game.entity;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.NdsMapData;
import org.pokemmo.gameserver.game.npc.CustomNpcCatalog;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemoveEntityPacket;
import org.server.bytes.ByteBufEx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDeleteServiceTest {
    @Test
    void durableSavePrecedesVisibilityAndMapRemoval() {
        MapData map = map();
        NpcEntity npc = npc();
        map.addEntity(npc);
        var previousSnapshot = map.getNpcEntityHashMap();
        var result = NpcDeleteService.remove(map, 553459519488L, selected -> {
            assertSame(npc, selected);
            assertTrue(selected.isLoad());
            assertTrue(selected.isCanInteract());
            assertSame(npc, map.getNpcEntityByGameId(553459519488L));
            return new CustomNpcCatalog.Disabled(100000, Path.of("npc_100000.jsonc"));
        });
        assertEquals(100000, result.entityIdx());
        assertEquals(553459519488L, result.npcId());
        assertFalse(npc.isLoad());
        assertFalse(npc.isCanInteract());
        assertNull(map.getNpcEntityByGameId(result.npcId()));
        assertFalse(map.containsNpcEntity(result.npcId()));
        assertSame(npc, previousSnapshot.get("npc_100000"));
    }

    @Test
    void failedSaveNeverRemovesOrDisablesNpc() {
        MapData map = map();
        NpcEntity npc = npc();
        map.addEntity(npc);
        assertThrows(IllegalStateException.class, () -> NpcDeleteService.remove(map, npc.getEntityGameId(), selected -> {
            throw new IllegalStateException("保存失败");
        }));
        assertTrue(npc.isLoad());
        assertTrue(npc.isCanInteract());
        assertSame(npc, map.getNpcEntityByGameId(npc.getEntityGameId()));
        assertThrows(IllegalStateException.class, () -> NpcDeleteService.remove(map, npc.getEntityGameId(), selected -> null));
        assertSame(npc, map.getNpcEntityByGameId(npc.getEntityGameId()));
    }

    @Test
    void unknownWrongMapOrRepeatedIdNeverReachesPersistence() {
        MapData map = map();
        NpcEntity npc = npc();
        AtomicInteger writes = new AtomicInteger();
        java.util.function.Function<NpcEntity, CustomNpcCatalog.Disabled> persist = selected -> {
            writes.incrementAndGet();
            return new CustomNpcCatalog.Disabled(100000, Path.of("npc_100000.jsonc"));
        };
        assertThrows(IllegalArgumentException.class, () -> NpcDeleteService.remove(map, npc.getEntityGameId(), persist));
        map.addEntity(npc);
        assertThrows(IllegalArgumentException.class, () -> NpcDeleteService.remove(map, 100000, persist));
        assertThrows(IllegalArgumentException.class, () -> NpcDeleteService.remove(map, -1, persist));
        assertEquals(0, writes.get());
        NpcDeleteService.remove(map, npc.getEntityGameId(), persist);
        assertThrows(IllegalArgumentException.class, () -> NpcDeleteService.remove(map, npc.getEntityGameId(), persist));
        assertEquals(1, writes.get());
    }

    @Test
    void staleEntityReferenceCannotRemoveReplacement() {
        MapData map = map();
        NpcEntity first = npc();
        NpcEntity replacement = npc();
        replacement.setEntityGameId(123L);
        map.addEntity(first);
        map.addEntity(replacement);
        assertFalse(map.removeEntity(first));
        assertSame(replacement, map.getNpcEntityByGameId(123L));
    }

    @Test
    void removePacketContainsOnlyRuntimeObjectIdLittleEndian() throws Exception {
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new SendRemoveEntityPacket(553459519488L).encode(buffer);
            assertEquals(8, buffer.readableBytes());
            assertEquals(553459519488L, buffer.readLongLE());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    private static MapData map() {
        MapData map = new NdsMapData(0, 5, 3, 20, 20, null, null, null, true, false, false);
        map.setMapKey("Test_Map");
        return map;
    }

    private static NpcEntity npc() {
        return new NpcEntity(553459519488L, true, "npc_100000", 0, 0, 0, 0, 0, 5, 3, 0, 68, 4, 3, 0);
    }
}
