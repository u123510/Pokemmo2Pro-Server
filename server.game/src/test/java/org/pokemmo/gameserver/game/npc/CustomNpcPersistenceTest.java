package org.pokemmo.gameserver.game.npc;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomNpcPersistenceTest {
    @TempDir
    Path temporary;

    @Test
    void savesOneFileAndRestoresStableIndexWithoutReusingRuntimeId() throws IOException {
        Path root = temporary.resolve("npc/custom");
        MapData first = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(first));
        assertEquals(100000, catalog.nextEntityIdx(first));
        NpcEntity npc = definition(100000, true, 4).toEntity(first);
        npc.setEntityGameId(987654321);
        Path saved = catalog.save(first, 100000, npc);
        first.addEntity(npc);
        assertEquals(root.resolve("kanto/Test_Map/npc_100000.jsonc"), saved);
        assertFalse(Files.readString(saved).contains("987654321"));
        MapData restoredMap = map();
        CustomNpcCatalog restored = new CustomNpcCatalog(root, List.of(restoredMap));
        NpcEntity restoredNpc = restoredMap.getNpcEntityHashMap().get("npc_100000");
        assertNotNull(restoredNpc);
        assertEquals(0, restoredNpc.getEntityGameId());
        assertEquals(4, restoredNpc.getX());
        assertEquals(3, restoredNpc.getY());
        assertEquals(68, restoredNpc.getNpcModelIndexId());
        assertEquals(100001, restored.nextEntityIdx(restoredMap));
    }

    @Test
    void neverWritesNativeMapFilesOrOverwritesExistingCustomFile() throws IOException {
        Path nativeFile = temporary.resolve("map/Test_Map.json");
        Files.createDirectories(nativeFile.getParent());
        Files.writeString(nativeFile, "{\"npcs\":[{\"entityIdx\":0}]}");
        String before = Files.readString(nativeFile);
        CustomNpcStore store = new CustomNpcStore(temporary.resolve("npc/custom"));
        CustomNpcDefinition definition = definition(100000, true, 4);
        Path file = store.saveNew(definition);
        String original = Files.readString(file);
        assertThrows(IOException.class, () -> store.saveNew(definition(100000, true, 5)));
        assertEquals(original, Files.readString(file));
        assertEquals(before, Files.readString(nativeFile));
        try (var paths = Files.walk(temporary.resolve("npc/custom"))) {
            assertFalse(paths.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }

    @Test
    void disabledEntriesAreNotSpawnedButTheirIndicesRemainReserved() throws IOException {
        Path root = temporary.resolve("npc/custom");
        new CustomNpcStore(root).saveNew(definition(100005, false, 4));
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        assertTrue(map.getNpcEntityHashMap().isEmpty());
        assertEquals(100006, catalog.nextEntityIdx(map));
    }

    @Test
    void corruptFilePreventsPartialLoadAndFurtherSpawnsButLeavesNativeNpcUntouched() throws IOException {
        Path root = temporary.resolve("npc/custom");
        CustomNpcStore store = new CustomNpcStore(root);
        store.saveNew(definition(100000, true, 4));
        Path broken = root.resolve("kanto/Test_Map/npc_100001.jsonc");
        Files.writeString(broken, "{\"version\":");
        MapData map = map();
        NpcEntity nativeNpc = new NpcEntity(7, true, "npc_0", 0, 0, 0, 0, 0, 5, 3, 0, 68, 2, 3, 0);
        map.addEntity(nativeNpc);
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        assertEquals(1, map.getNpcEntityHashMap().size());
        assertSame(nativeNpc, map.getNpcEntityByGameId(7));
        assertThrows(IllegalArgumentException.class, () -> catalog.nextEntityIdx(map));
        assertEquals("{\"version\":", Files.readString(broken));
    }

    @Test
    void nativeNameCollisionNeverOverwritesOrRenumbersSavedNpc() throws IOException {
        Path root = temporary.resolve("npc/custom");
        new CustomNpcStore(root).saveNew(definition(100000, true, 4));
        MapData map = map();
        NpcEntity original = definition(100000, true, 6).toEntity(map);
        map.addEntity(original);
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        assertSame(original, map.getNpcEntityHashMap().get("npc_100000"));
        assertEquals(1, map.getNpcEntityHashMap().size());
        assertThrows(IllegalArgumentException.class, () -> catalog.nextEntityIdx(map));
    }

    @Test
    void overlappingCustomEntriesAreRejectedAsOneBatch() throws IOException {
        Path root = temporary.resolve("npc/custom");
        CustomNpcStore store = new CustomNpcStore(root);
        store.saveNew(definition(100000, true, 4));
        store.saveNew(definition(100001, true, 4));
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        assertTrue(map.getNpcEntityHashMap().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> catalog.nextEntityIdx(map));
    }

    @Test
    void codecAcceptsCommentsButRejectsDuplicateUnknownAndMissingFields() throws IOException {
        String json = CustomNpcCodec.write(definition(100000, true, 4));
        assertEquals(definition(100000, true, 4), CustomNpcCodec.read(new StringReader("// comment\n" + json)));
        for (String invalid : List.of("{}", json.replace("{", "{\"version\":1,"),
                json.replace("{", "{\"script\":0,"), json + "{}",
                json.replace("100000", "100000.5"), json.replace("100000", "\"100000\""),
                json.replace("100000", "1"), json.replace("true", "null"))) {
            assertThrows(Exception.class, () -> CustomNpcCodec.read(new StringReader(invalid)));
        }
    }

    @Test
    void rejectsEscapingNamesAndMismatchedCanonicalFilename() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new CustomNpcDefinition(1, true,
                "../Other", 0, 100000, 68, 0, 0, 0, 0, 4, 3, 0, 2));
        Path root = temporary.resolve("npc/custom");
        CustomNpcStore store = new CustomNpcStore(root);
        Path saved = store.saveNew(definition(100000, true, 4));
        Files.move(saved, saved.resolveSibling("npc_100099.jsonc"));
        assertThrows(IOException.class, store::readAll);
    }

    @Test
    void rejectsOversizedFilesAndUnknownMaps() throws IOException {
        Path root = temporary.resolve("npc/custom");
        CustomNpcStore store = new CustomNpcStore(root);
        Path saved = store.saveNew(definition(100000, true, 4));
        CustomNpcCatalog missingMap = new CustomNpcCatalog(root, List.of());
        assertThrows(IllegalArgumentException.class, () -> missingMap.nextEntityIdx(map()));
        Files.writeString(saved, " ".repeat(16385), StandardCharsets.UTF_8);
        assertThrows(IOException.class, store::readAll);
    }

    @Test
    void writeFailureDoesNotReserveIdentityOrChangeNativeMap() throws IOException {
        Path root = temporary.resolve("blocked/npc/custom");
        MapData map = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(map));
        Files.writeString(temporary.resolve("blocked"), "不是目录");
        NpcEntity npc = definition(100000, true, 4).toEntity(map);
        assertThrows(UncheckedIOException.class, () -> catalog.save(map, 100000, npc));
        assertTrue(map.getNpcEntityHashMap().isEmpty());
        assertEquals(100000, catalog.nextEntityIdx(map));
    }

    private static CustomNpcDefinition definition(int index, boolean enabled, int x) {
        return new CustomNpcDefinition(1, enabled, "Test_Map", 0, index, 68, 0, 0, 0, 0, x, 3, 0, 2);
    }

    private static MapData map() {
        MapData map = new NdsMapData(0, 5, 3, 20, 20, null, null, null, true, false, false);
        map.setMapKey("Test_Map");
        return map;
    }
}
