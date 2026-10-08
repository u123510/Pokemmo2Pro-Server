package org.pokemmo.gameserver.game.npc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.NdsMapData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomNpcDeletionTest {
    @TempDir
    Path root;

    @Test
    void disablePersistsAcrossRestartAndReservesFixedIndex() throws IOException {
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        NpcEntity npc = definition().toEntity(map);
        Path file = catalog.save(map, 100000, npc);
        map.addEntity(npc);
        npc.setEntityGameId(553459519488L);
        CustomNpcCatalog.Disabled disabled = catalog.disable(map, npc);
        assertEquals(file, disabled.file());
        assertEquals(100000, disabled.entityIdx());
        assertTrue(Files.exists(file));
        assertEquals(definition().disabled(), new CustomNpcStore(root).readAll().get(0).definition());
        // Persistence alone does not mutate the live map; the deletion service publishes that change.
        assertSame(npc, map.getNpcEntityByGameId(553459519488L));
        assertTrue(npc.isLoad());
        MapData restart = map();
        CustomNpcCatalog reloaded = new CustomNpcCatalog(root, List.of(restart));
        assertTrue(restart.getNpcEntityHashMap().isEmpty());
        assertEquals(100001, reloaded.nextEntityIdx(restart));
    }

    @Test
    void rejectsNativeUnsavedAndForgedSameNameEntities() {
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        NpcEntity nativeNpc = new NpcEntity(1, true, "npc_0", 0, 0, 0, 0, 0, 5, 3, 0, 68, 1, 1, 0);
        map.addEntity(nativeNpc);
        assertThrows(IllegalArgumentException.class, () -> catalog.disable(map, nativeNpc));
        NpcEntity unsaved = definition().toEntity(map);
        map.addEntity(unsaved);
        assertThrows(IllegalArgumentException.class, () -> catalog.disable(map, unsaved));
        map.removeEntity(unsaved);
        NpcEntity saved = definition().toEntity(map);
        catalog.save(map, 100000, saved);
        map.addEntity(saved);
        NpcEntity forged = definition().toEntity(map);
        assertThrows(IllegalArgumentException.class, () -> catalog.disable(map, forged));
        map.addEntity(forged);
        assertThrows(IllegalArgumentException.class, () -> catalog.disable(map, saved));
        assertTrue(nativeNpc.isLoad());
    }

    @Test
    void externallyEditedOrMissingFileDoesNotChangeCatalogOrNpc() throws IOException {
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        NpcEntity npc = definition().toEntity(map);
        Path file = catalog.save(map, 100000, npc);
        map.addEntity(npc);
        String edited = CustomNpcCodec.write(definition()).replace("\"spriteId\": 68", "\"spriteId\": 69");
        Files.writeString(file, edited);
        assertThrows(UncheckedIOException.class, () -> catalog.disable(map, npc));
        assertEquals(edited, Files.readString(file));
        assertTrue(npc.isLoad());
        Files.delete(file);
        assertThrows(UncheckedIOException.class, () -> catalog.disable(map, npc));
        assertFalse(Files.exists(file));
        assertSame(npc, map.getNpcEntityHashMap().get("npc_100000"));
    }

    @Test
    void retryAfterDiskCommitOnlyIsIdempotent() throws IOException {
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        NpcEntity npc = definition().toEntity(map);
        Path file = catalog.save(map, 100000, npc);
        map.addEntity(npc);
        // Simulate the first attempt committing the file but losing the success response.
        new CustomNpcStore(root).disable(definition());
        String beforeRetry = Files.readString(file);
        assertEquals(file, catalog.disable(map, npc).file());
        assertEquals(beforeRetry, Files.readString(file));
        assertThrows(IllegalArgumentException.class, () -> catalog.disable(map, npc));
    }

    private static CustomNpcDefinition definition() {
        return new CustomNpcDefinition(1, true, "Test_Map", 0, 100000, 68, 0, 0, 0, 0, 4, 3, 0, 2);
    }

    private static MapData map() {
        MapData map = new NdsMapData(0, 5, 3, 20, 20, null, null, null, true, false, false);
        map.setMapKey("Test_Map");
        return map;
    }
}
