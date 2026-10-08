package org.pokemmo.gameserver.game.shop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.map.MapData;
import org.server.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopNpcBindingsTest {
    private static final String MAP = "CeruleanCity_Mart";

    @Test
    void parsesOmittedAndExplicitEmptyBindingsDifferently() {
        assertNull(parse("{}"));
        assertEquals(List.of(), parse("{\"npcs\":[]}"));
        assertEquals(List.of(target(1)), parse("""
                {"npcs":[{"map":"CeruleanCity_Mart","entityIdx":1}]}
                """));
    }

    @Test
    void rejectsMalformedSelectorsWithoutCoercion() {
        for (String value : List.of("null", "{}", "[null]",
                "[{\"map\":\"CeruleanCity_Mart\"}]",
                "[{\"map\":1,\"entityIdx\":0}]",
                "[{\"map\":\"CeruleanCity_Mart\",\"entityIdx\":\"1\"}]",
                "[{\"map\":\"CeruleanCity_Mart\",\"entityIdx\":0.5}]",
                "[{\"map\":\"CeruleanCity_Mart\",\"entityIdx\":-1}]",
                "[{\"map\":\"CeruleanCity_Mart\",\"entityIdx\":2147483648}]",
                "[{\"map\":\"CeruleanCity_Mart\",\"entityIdx\":1,\"script\":\"ignored\"}]")) {
            assertThrows(IllegalArgumentException.class, () -> parse("{\"npcs\":" + value + "}"), value);
        }
        for (String name : List.of("", " ", MAP + ".json", "../" + MAP, "kanto\\" + MAP, " " + MAP)) {
            assertThrows(IllegalArgumentException.class, () -> new ShopNpcBinding(name, 0));
        }
    }

    @Test
    void rejectsDuplicateNullAndOversizedBindingLists() {
        assertThrows(IllegalArgumentException.class, () -> shop("mart", List.of(target(1), target(1))));
        List<ShopNpcBinding> bindings = new ArrayList<>();
        bindings.add(null);
        assertThrows(IllegalArgumentException.class, () -> shop("mart", bindings));
        bindings.clear();
        for (int i = 0; i <= ShopDefinition.MAX_NPCS; i++) bindings.add(target(i));
        assertThrows(IllegalArgumentException.class, () -> shop("mart", bindings));
    }

    @Test
    void copiesBindingsBeforePublishing() {
        List<ShopNpcBinding> source = new ArrayList<>(List.of(target(1)));
        ShopDefinition shop = shop("mart", source);
        source.clear();
        assertEquals(List.of(target(1)), shop.npcs());
        assertThrows(UnsupportedOperationException.class, () -> shop.npcs().clear());
    }

    @Test
    void bindsOrdinaryNpcWithoutChangingScriptOrLegacyShopId() {
        MapData map = map(MAP, 0, 5, 8, 0, 1);
        NpcEntity npc = map.getNpcEntityHashMap().get("npc_1");
        npc.setInteractionScriptName("CeruleanCity_Mart_EventScript_Woman");
        ShopCatalog.Snapshot snapshot = snapshot(List.of(map), shop("mart", List.of(target(1))));
        assertEquals("mart", snapshot.resolveShopId(map, npc));
        assertNull(snapshot.resolveShopId(map, map.getNpcEntityHashMap().get("npc_0")));
        assertNull(npc.getShopId());
        assertEquals("CeruleanCity_Mart_EventScript_Woman", npc.getInteractionScriptName());
        npc.setEntityGameId(987654321L);
        assertEquals("mart", snapshot.resolveShopId(map, npc));
    }

    @Test
    void switchingNpcSuppressesOldMapBindingAndPreservesPreviousSnapshot() {
        MapData map = map(MAP, 0, 5, 8, 0, 1);
        NpcEntity previous = map.getNpcEntityHashMap().get("npc_0");
        NpcEntity next = map.getNpcEntityHashMap().get("npc_1");
        previous.setShopId("mart");
        ShopCatalog.Snapshot old = snapshot(List.of(map), shop("mart", List.of(target(0))));
        ShopCatalog.Snapshot updated = snapshot(List.of(map), shop("mart", List.of(target(1))));
        assertEquals("mart", old.resolveShopId(map, previous));
        assertNull(updated.resolveShopId(map, previous));
        assertEquals("mart", updated.resolveShopId(map, next));
        assertEquals("mart", previous.getShopId());
        assertEquals("mart", old.resolveShopId(map, previous));
    }

    @Test
    void emptyArrayUnbindsWhileOmittedFieldKeepsLegacyBehavior() {
        MapData map = map(MAP, 0, 5, 8, 0);
        NpcEntity npc = map.getNpcEntityHashMap().get("npc_0");
        npc.setShopId("mart");
        assertEquals("mart", snapshot(List.of(map), shop("mart", null)).resolveShopId(map, npc));
        assertNull(snapshot(List.of(map), shop("mart", List.of())).resolveShopId(map, npc));
        // Missing legacy shop IDs remain visible to ShopService for Chinese error feedback.
        assertEquals("mart", snapshot(List.of(map)).resolveShopId(map, npc));
    }

    @Test
    void explicitNpcCanOverrideAnotherLegacyShop() {
        MapData map = map(MAP, 0, 5, 8, 0);
        NpcEntity npc = map.getNpcEntityHashMap().get("npc_0");
        npc.setShopId("legacy");
        ShopCatalog.Snapshot snapshot = snapshot(List.of(map), shop("legacy", null),
                shop("new", List.of(target(0))));
        assertEquals("new", snapshot.resolveShopId(map, npc));
    }

    @Test
    void supportsMultipleNpcsAndRejectsCrossMapIdentity() {
        MapData map = map(MAP, 0, 5, 8, 0, 1);
        MapData other = map("Other_Mart", 1, 5, 8, 0);
        ShopCatalog.Snapshot snapshot = snapshot(List.of(map, other),
                shop("mart", List.of(target(0), target(1))));
        assertEquals("mart", snapshot.resolveShopId(map, map.getNpcEntityHashMap().get("npc_0")));
        assertEquals("mart", snapshot.resolveShopId(map, map.getNpcEntityHashMap().get("npc_1")));
        assertNull(snapshot.resolveShopId(other, other.getNpcEntityHashMap().get("npc_0")));
        assertNull(snapshot.resolveShopId(map, other.getNpcEntityHashMap().get("npc_0")));
    }

    @Test
    void rejectsEveryConflictParticipantRegardlessOfFileOrder() {
        MapData map = map(MAP, 0, 5, 8, 0, 1, 2, 3);
        Map<String, ShopDefinition> shops = candidates(
                shop("first", List.of(target(0), target(1))),
                shop("second", List.of(target(1), target(2))),
                shop("third", List.of(target(2))),
                shop("independent", List.of(target(3))));
        List<String> errors = new ArrayList<>();
        ShopNpcBindings bindings = compile(shops, List.of(map), errors);
        assertEquals(List.of("independent"), List.copyOf(shops.keySet()));
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(error -> error.contains("NPC 绑定冲突") && error.contains(".jsonc")));
        assertNull(bindings.resolveShopId(map, map.getNpcEntityHashMap().get("npc_0"), shops));
        assertEquals("independent", bindings.resolveShopId(map, map.getNpcEntityHashMap().get("npc_3"), shops));
    }

    @Test
    void rejectsMissingNpcAndUnknownOrAmbiguousMapNames() {
        MapData map = map(MAP, 0, 5, 8, 0);
        for (List<MapData> maps : List.of(List.<MapData>of(), List.of(map),
                List.of(map, map(MAP, 1, 5, 8, 0, 99)))) {
            Map<String, ShopDefinition> shops = candidates(shop("bad", List.of(target(99))), shop("legacy", null));
            List<String> errors = new ArrayList<>();
            compile(shops, maps, errors);
            assertFalse(shops.containsKey("bad"));
            assertTrue(shops.containsKey("legacy"));
            assertEquals(1, errors.size());
        }
    }

    private static List<ShopNpcBinding> parse(String json) {
        return ShopConfigLoader.readNpcBindings(JsonParser.parseString(json).getAsJsonObject());
    }

    private static ShopNpcBinding target(int index) {
        return new ShopNpcBinding(MAP, index);
    }

    private static ShopDefinition shop(String id, List<ShopNpcBinding> bindings) {
        return new ShopDefinition(id, true, true, List.of(new ShopItem(5004, 200, 100)), bindings);
    }

    private static Map<String, ShopDefinition> candidates(ShopDefinition... shops) {
        Map<String, ShopDefinition> result = new HashMap<>();
        for (ShopDefinition shop : shops) result.put(shop.shopId(), shop);
        return result;
    }

    private static ShopNpcBindings compile(Map<String, ShopDefinition> shops, List<MapData> maps,
                                           List<String> errors) {
        Map<String, Path> origins = new HashMap<>();
        shops.keySet().forEach(id -> origins.put(id, Path.of(id + ".jsonc")));
        return ShopNpcBindings.compile(shops, maps, origins, errors);
    }

    private static ShopCatalog.Snapshot snapshot(List<MapData> maps, ShopDefinition... definitions) {
        Map<String, ShopDefinition> shops = candidates(definitions);
        List<String> errors = new ArrayList<>();
        ShopNpcBindings bindings = compile(shops, maps, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return new ShopCatalog.Snapshot(1, shops, bindings);
    }

    private static MapData map(String name, int region, int bank, int id, int... npcIndices) {
        MapData map = new MapData(region, bank, id, 0, 0, 0, 20, 20, 0, 0, 0, 0, 0, 0, null, null, null) {
            @Override
            public boolean checkIsWalkable(int x, int y) {
                return true;
            }

            @Override
            public void loadArroundEntity(Session session) {
            }
        };
        map.setMapKey(name);
        for (int index : npcIndices) {
            map.addEntity(new NpcEntity(index + 1, true, "npc_" + index, 0, 0,
                    0, 0, region, bank, id, 0, 68, 2, 3, 0));
        }
        return map;
    }
}
