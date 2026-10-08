package org.pokemmo.gameserver.game.npc;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pokemmo.gameserver.game.entity.EventNpcSpawnRequest;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.NdsMapData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventNpcPersistenceTest {
    @TempDir
    Path root;

    @Test
    void existingVersionOneRetainsOriginalSchemaAndDefaultAppearance() throws IOException {
        CustomNpcDefinition legacy = legacy();
        String json = CustomNpcCodec.write(legacy);
        assertEquals(14, JsonParser.parseString(json).getAsJsonObject().size());
        assertFalse(json.contains("\"eventId\""));
        CustomNpcDefinition parsed = CustomNpcCodec.read(new StringReader(json));
        assertEquals(legacy, parsed);
        NpcEntity npc = parsed.toEntity(map());
        assertFalse(npc.isUnk6());
        assertFalse(npc.isSpriteScaleOverride());
        assertEquals(-1, parsed.eventId());
    }

    @Test
    void eventCategoryAndAppearanceSurviveSaveRestartAndDeletion() throws IOException {
        MapData original = map();
        CustomNpcCatalog catalog = new CustomNpcCatalog(root, List.of(original));
        EventNpcSpawnRequest request = EventNpcSpawnRequest.parse(
                "0 248 10 0 0 0 0 -1 -1 false true false 2.0 0".split(" "));
        NpcEntity npc = new NpcEntity(77, true, "npc_100000", 1, 0, 0, 0, 0, 5, 3, 10, 248, 4, 3, 2);
        request.appearance().applyTo(npc);
        Path path = catalog.save(original, 100000, npc, request.appearance());
        original.addEntity(npc);
        CustomNpcDefinition saved = new CustomNpcStore(root).readAll().get(0).definition();
        assertEquals(2, saved.version());
        assertEquals(0, saved.eventId());
        assertEquals(17, JsonParser.parseString(Files.readString(path)).getAsJsonObject().size());
        MapData restart = map();
        CustomNpcCatalog restored = new CustomNpcCatalog(root, List.of(restart));
        NpcEntity restoredNpc = restart.getNpcEntityHashMap().get("npc_100000");
        assertNotNull(restoredNpc);
        assertTrue(restoredNpc.isUnk6());
        assertTrue(restoredNpc.isSpriteScaleOverride());
        assertEquals(2.0f, restoredNpc.getSpriteScaleOverride());
        assertEquals(0, restoredNpc.getEntityGameId());
        restored.disable(restart, restoredNpc);
        CustomNpcDefinition disabled = new CustomNpcStore(root).readAll().get(0).definition();
        assertFalse(disabled.enabled());
        assertEquals(saved.disabled(), disabled);
        MapData afterDelete = map();
        assertEquals(100001, new CustomNpcCatalog(root, List.of(afterDelete)).nextEntityIdx(afterDelete));
        assertTrue(afterDelete.getNpcEntityHashMap().isEmpty());
    }

    @Test
    void rejectsVersionMismatchMissingExtensionAndNonFiniteScale() {
        String v1 = CustomNpcCodec.write(legacy());
        assertThrows(IllegalArgumentException.class, () -> CustomNpcCodec.read(new StringReader(
                v1.replace("\"version\": 1", "\"version\": 2"))));
        assertThrows(IllegalArgumentException.class, () -> CustomNpcCodec.read(new StringReader(
                v1.replace("{", "{\"eventId\":0,"))));
        CustomNpcDefinition event = new CustomNpcDefinition(2, true, "Test_Map", 0, 100000,
                248, 10, 0, 0, 0, 4, 3, 2, 1, 0, true, 2.0f);
        String v2 = CustomNpcCodec.write(event);
        for (String malformed : List.of(
                v2.replace("\"spriteScale\": 2.0", "\"spriteScale\": 1e100"),
                v2.replace("\"spriteScale\": 2.0", "\"spriteScale\": 0"),
                v2.replace("\"sparkles\": true", "\"sparkles\": \"true\""),
                v2.replace("\"eventId\": 0", "\"eventId\": 256"),
                v2.replace("{", "{\"eventId\":0,"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> CustomNpcCodec.read(new StringReader(malformed)));
        }
    }

    private static CustomNpcDefinition legacy() {
        return new CustomNpcDefinition(1, true, "Test_Map", 0, 100000, 68, 0, 0, 0, 0, 4, 3, 0, 2);
    }

    private static MapData map() {
        MapData map = new NdsMapData(0, 5, 3, 20, 20, null, null, null, true, false, false);
        map.setMapKey("Test_Map");
        return map;
    }
}
