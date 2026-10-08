package org.pokemmo.gameserver.game.entity;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.NdsMapData;
import org.pokemmo.gameserver.game.npc.CustomNpcDefinition;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddGameEntityPacket;
import org.server.bytes.ByteBufEx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSpawnServiceTest {
    @Test
    void acceptsCapturedZeroArgumentsAndRejectsInvalidFields() {
        new NpcSpawnRequest(0, 0, 0, 0, 0, 0).validateForMap(0);
        assertThrows(IllegalArgumentException.class, () -> request(-1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> request(10001, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> request(68, 1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> request(68, 0, 5, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> request(68, 0, 0, 128, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> request(68, 0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> request(68, 0, 0, 0, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> request(68, 0, 0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> still().validateForMap(4));
    }

    @Test
    void movementUsesMapRegionNotSpriteRegionAndExcludesCustomEventAi() {
        new NpcSpawnRequest(68, 0, 3, 24, 4, 4).validateForMap(0);
        assertThrows(IllegalArgumentException.class, () -> new NpcSpawnRequest(68, 0, 0, 24, 0, 0).validateForMap(3));
        new NpcSpawnRequest(68, 0, 0, 4, 0, 0).validateForMap(3);
        assertThrows(IllegalArgumentException.class, () -> new NpcSpawnRequest(68, 0, 3, 4, 0, 0).validateForMap(0));
        assertThrows(IllegalArgumentException.class, () -> new NpcSpawnRequest(68, 0, 0, 98, 0, 0).validateForMap(0));
        assertThrows(IllegalArgumentException.class, () -> new NpcSpawnRequest(68, 0, 3, 127, 0, 0).validateForMap(3));
    }

    @Test
    void createsInFrontAndFacesThePlayerInEveryDirection() {
        int[][] coordinates = {{5, 6}, {5, 4}, {4, 5}, {6, 5}};
        for (int toward = 0; toward < 4; toward++) {
            MapData map = map(0, true);
            NpcSpawnService.Spawned result = create(map, still(), 5, 5, 2, toward, () -> 123L);
            NpcEntity npc = result.entity();
            assertEquals(coordinates[toward][0], npc.getX());
            assertEquals(coordinates[toward][1], npc.getY());
            assertEquals(toward ^ 1, npc.getToward());
            assertEquals(2, npc.getZ());
            assertTrue(npc.isLoad());
            assertTrue(npc.isCanInteract());
            assertNull(npc.getInteractionScriptName());
            assertNull(npc.getShopId());
            assertSame(npc, map.getNpcEntityByGameId(123L));
        }
    }

    @Test
    void doesNotOverwriteExistingNpcOrMutatePublishedMapSnapshot() {
        MapData map = map(0, true);
        NpcEntity original = npc(10, "npc_0", 2, 2, 2, 0);
        map.addEntity(original);
        HashMap<String, NpcEntity> previous = map.getNpcEntityHashMap();
        NpcSpawnService.Spawned spawned = create(map, still(), 5, 5, 2, 0, () -> 11L);
        assertEquals(100000, spawned.entityIdx());
        assertEquals(1, previous.size());
        assertFalse(previous.containsKey("npc_100000"));
        assertEquals(2, map.getNpcEntityHashMap().size());
        assertSame(original, map.getNpcEntityByGameId(10));
    }

    @Test
    void rejectsBlockedOutOfBoundsAndOccupiedTilesWithoutAllocatingId() {
        AtomicLong calls = new AtomicLong();
        java.util.function.LongSupplier ids = () -> calls.incrementAndGet();
        assertThrows(IllegalArgumentException.class,
                () -> create(map(0, false), still(), 5, 5, 0, 0, ids));
        assertThrows(IllegalArgumentException.class,
                () -> create(map(0, true), still(), 0, 0, 0, 1, ids));
        assertThrows(IllegalArgumentException.class,
                () -> create(map(0, true), still(), 19, 19, 0, 3, ids));
        MapData occupied = map(0, true);
        occupied.addEntity(npc(10, "npc_0", 5, 6, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> create(occupied, still(), 5, 5, 2, 0, ids));
        assertEquals(1, occupied.getNpcEntityHashMap().size());
        assertEquals(0, calls.get());
    }

    @Test
    void permitsSeparateNdsLayersButRejectsInvalidHeightAndDirection() {
        MapData map = map(3, true);
        map.addEntity(npc(10, "npc_0", 5, 6, 0, 3));
        assertEquals(100000, create(map, still(), 5, 5, 1, 0, () -> 11L).entityIdx());
        assertThrows(IllegalArgumentException.class,
                () -> create(map, still(), 5, 5, 128, 0, () -> 12L));
        assertThrows(IllegalArgumentException.class,
                () -> create(map, still(), 5, 5, 0, 4, () -> 12L));
    }

    @Test
    void rejectsDuplicateIdAndMapLimitWithoutPartialInsertion() {
        MapData map = map(0, true);
        map.addEntity(npc(10, "npc_0", 1, 1, 0, 0));
        assertThrows(IllegalStateException.class,
                () -> create(map, still(), 5, 5, 0, 0, () -> 10L));
        assertThrows(IllegalStateException.class,
                () -> create(map, still(), 5, 5, 0, 0, () -> 0L));
        assertEquals(1, map.getNpcEntityHashMap().size());
        for (int i = 1; i < NpcSpawnService.MAX_MAP_NPCS; i++) {
            map.addEntity(npc(i + 10, "npc_" + i, 1, 1, 0, 0));
        }
        assertThrows(IllegalArgumentException.class,
                () -> create(map, still(), 5, 5, 0, 0, () -> 2000L));
        assertEquals(NpcSpawnService.MAX_MAP_NPCS, map.getNpcEntityHashMap().size());
    }

    @Test
    void concurrentSpawnsAtSameTileCreateOnlyOneNpc() throws Exception {
        MapData map = map(0, true);
        AtomicLong ids = new AtomicLong(10);
        var workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                try {
                    create(map, still(), 5, 5, 0, 0, ids::incrementAndGet);
                    return true;
                } catch (IllegalArgumentException rejected) {
                    return false;
                }
            };
            var first = workers.submit(attempt);
            var second = workers.submit(attempt);
            assertTrue(first.get() ^ second.get());
            assertEquals(1, map.getNpcEntityHashMap().size());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void reloadingNdsMapDoesNotReplaceAlreadyAssignedIds() {
        NdsMapData map = map(3, true);
        NpcEntity npc = create(map, still(), 5, 5, 0, 0, () -> 777L).entity();
        // No session is needed when all NPCs already have IDs.
        map.loadArroundEntity(null);
        map.loadArroundEntity(null);
        assertEquals(777L, npc.getEntityGameId());
    }

    @Test
    void npcPacketRetainsNativeFieldOrderAndNdsMapByteOrder() throws Exception {
        NpcEntity npc = create(map(3, true),
                new NpcSpawnRequest(68, 0, 0, 2, 3, 4), 5, 5, 2, 3, () -> 0x0102030405060708L).entity();
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new SendAddGameEntityPacket(npc).encode(buffer);
            assertEquals(26, buffer.readableBytes());
            assertEquals(0x0102030405060708L, buffer.readLongLE());
            assertEquals(0, buffer.readUnsignedByte());
            assertEquals(68, buffer.readUnsignedShortLE());
            assertEquals(2, buffer.readUnsignedByte());
            assertEquals(2, buffer.readUnsignedByte());
            assertEquals(3, buffer.readUnsignedByte());
            assertEquals(4, buffer.readUnsignedByte());
            assertEquals(3, buffer.readUnsignedByte());
            assertEquals(155, buffer.readUnsignedByte());
            assertEquals(1, buffer.readUnsignedByte());
            assertEquals(6, buffer.readShortLE());
            assertEquals(5, buffer.readShortLE());
            assertEquals(2, buffer.readByte());
            assertEquals(2, buffer.readUnsignedByte());
            assertEquals(8, buffer.readUnsignedShortLE());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void failedPersistenceNeverPublishesNpc() {
        MapData map = map(0, true);
        assertThrows(IllegalStateException.class, () -> NpcSpawnService.createNpc(
                map, still(), 5, 5, 0, 0, () -> 55L, 100000, npc -> {
                    assertTrue(map.getNpcEntityHashMap().isEmpty());
                    throw new IllegalStateException("模拟文件写入失败");
                }));
        assertTrue(map.getNpcEntityHashMap().isEmpty());
        assertThrows(IllegalStateException.class, () -> NpcSpawnService.createNpc(
                map, still(), 5, 5, 0, 0, () -> 55L, 100000, npc -> null));
        assertTrue(map.getNpcEntityHashMap().isEmpty());
    }

    @Test
    void successfulPersistencePrecedesMapInsertion() {
        MapData map = map(0, true);
        Path saved = Path.of("npc_100000.jsonc");
        NpcSpawnService.Spawned result = NpcSpawnService.createNpc(
                map, still(), 5, 5, 0, 0, () -> 55L, 100000, npc -> {
                    assertTrue(map.getNpcEntityHashMap().isEmpty());
                    assertEquals("npc_100000", npc.getNpcName());
                    return saved;
                });
        assertEquals(saved, result.file());
        assertSame(result.entity(), map.getNpcEntityByGameId(55L));
    }

    private static NpcSpawnService.Spawned create(MapData map, NpcSpawnRequest request, int x, int y,
                                                 int z, int toward, java.util.function.LongSupplier ids) {
        synchronized (map) {
            int index = CustomNpcDefinition.FIRST_ENTITY_IDX;
            while (map.getNpcEntityHashMap().containsKey("npc_" + index)) index++;
            return NpcSpawnService.createNpc(map, request, x, y, z, toward, ids, index,
                    npc -> Path.of(npc.getNpcName() + ".jsonc"));
        }
    }

    private static void request(int sprite, int script, int region, int movement, int x, int y) {
        new NpcSpawnRequest(sprite, script, region, movement, x, y);
    }

    private static NpcSpawnRequest still() {
        return new NpcSpawnRequest(68, 0, 0, 0, 0, 0);
    }

    private static NdsMapData map(int region, boolean walkable) {
        NdsMapData map = new NdsMapData(region, 1, 155, 20, 20, null, null, null, walkable, false, false);
        map.setMapKey("Test_Map");
        return map;
    }

    private static NpcEntity npc(long id, String name, int x, int y, int z, int region) {
        return new NpcEntity(id, true, name, 0, 0, 0, 0, region, 1, 155, 0, 68, x, y, z);
    }
}
