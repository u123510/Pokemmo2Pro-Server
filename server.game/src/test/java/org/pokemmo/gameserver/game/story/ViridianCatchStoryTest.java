package org.pokemmo.gameserver.game.story;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapFile;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.ServerType;
import org.server.Session;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViridianCatchStoryTest {
    private static final Path RESOURCE = Files.isDirectory(Path.of("resource"))
            ? Path.of("resource") : Path.of("../resource");
    private static ViridianCatchCatalog catalog;

    @BeforeAll
    static void resourcesOnly() {
        new PokemonManager(RESOURCE.resolve("pokemon/Pokemon.jsonc").toString());
        new ItemManager(RESOURCE.resolve("item/Item.bin").toString());
        catalog = new ViridianCatchCatalog(RESOURCE.resolve("story"));
    }

    @Test
    void catalogUsesNativeTutorialTextAndFixedWeedleEncounter() {
        assertTrue(catalog.enabled());
        assertEquals(13, catalog.wildSpecies());
        assertEquals(5, catalog.wildLevel());
        assertEquals(1563982, catalog.text("introduction"));
        assertEquals(1564315, catalog.text("completed"));
    }

    @Test
    void tutorialRequiresTheOakChapterToBeFinished() {
        CharacterManager beforeReward = manager((short) 5, false);
        CharacterManager afterReward = manager((short) 6, false);
        assertFalse(ViridianCatchStory.eligible(beforeReward));
        assertTrue(ViridianCatchStory.eligible(afterReward));
    }

    @Test
    void tutorialMapAndNpcUseTheVisibleOldManModel() {
        MapData map = map("viridian_city/ViridianCity");
        assertNotNull(map);
        NpcEntity npc = map.getNpcEntityHashMap().get("npc_3");
        assertNotNull(npc);
        assertEquals(32, npc.getNpcModelIndexId());
    }

    @Test
    void completedTutorialRemainsEligibleForNativeReturnDialogue() {
        CharacterManager manager = manager((short) 6, true);
        assertTrue(ViridianCatchStory.eligible(manager));
        assertTrue(manager.getPalletStory().viridianCatchComplete);
    }

    @Test
    void onlyViridianTutorialNpcMatches() {
        CharacterManager manager = manager((short) 6, false);
        MapData map = map("viridian_city/ViridianCity");
        assertNotNull(map);
        manager.getCurrentMapDatas()[0] = map;
        NpcEntity tutorialNpc = map.getNpcEntityHashMap().get("npc_3");
        NpcEntity otherNpc = map.getNpcEntityHashMap().get("npc_2");
        assertNotNull(tutorialNpc);
        assertNotNull(otherNpc);
        assertTrue(ViridianCatchStory.isTutorialNpc(manager, tutorialNpc));
        assertFalse(ViridianCatchStory.isTutorialNpc(manager, otherNpc));
    }

    private static MapData map(String key) {
        String previous = System.getProperty("openmmo.map.npcs.enabled");
        System.setProperty("openmmo.map.npcs.enabled", "true");
        try {
            MapFile file = new MapFile();
            file.setJsonFile(RESOURCE.resolve("map/kanto/" + key + ".json").toFile());
            return file.parseMapData();
        } finally {
            if (previous == null) {
                System.clearProperty("openmmo.map.npcs.enabled");
            } else {
                System.setProperty("openmmo.map.npcs.enabled", previous);
            }
        }
    }

    private static CharacterManager manager(short stage, boolean complete) {
        Session session = new TestSession();
        ScriptManager scripts = new ScriptManager(new String[0]);
        scripts.setViridianCatchStory(catalog);
        CharacterManager manager = new CharacterManager.Builder()
                .setCharacterSession(session)
                .setScriptManager(scripts)
                .setSnowflakeIdGenerator(new SnowflakeIdGenerator(1, 1))
                .build();
        CharacterData character = new CharacterData.Builder().build();
        character.getPlayerEntity().setEntityGameId(700L + stage);
        character.setFirstPartnerStatus(new short[]{0, 3, 3, 3, 3});
        character.setOakLabStatus(stage);
        manager.setCharacterData(character);
        manager.getPalletStory().progress = PalletStoryProgress.from(character);
        manager.getPalletStory().viridianCatchComplete = complete;
        session.attr(org.pokemmo.gameserver.protocol.GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(manager);
        return manager;
    }

    private static final class TestSession extends Session {
        private final DefaultAttributeMap attributes = new DefaultAttributeMap();

        TestSession() {
            super(null, null, Side.SERVER, ServerType.GAME);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return attributes.attr(key);
        }
    }
}
