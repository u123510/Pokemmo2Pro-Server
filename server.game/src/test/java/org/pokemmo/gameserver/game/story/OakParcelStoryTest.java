package org.pokemmo.gameserver.game.story;

import java.nio.file.Files;
import java.nio.file.Path;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapFile;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.ServerType;
import org.server.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OakParcelStoryTest {
    private static final Path RESOURCE = Files.isDirectory(Path.of("resource")) ? Path.of("resource") : Path.of("../resource");
    private static OakParcelCatalog catalog;

    @BeforeAll
    static void resourcesOnly() {
        new ItemManager(RESOURCE.resolve("item/Item.bin").toString());
        catalog = new OakParcelCatalog(RESOURCE.resolve("story"));
    }

    @Test
    void defaultAndLegacyCheckpointsResolveWithoutReplayingCompletedRewards() {
        assertEquals(OakParcelProgress.Phase.OPENING, new OakParcelProgress((short) 3, (short) 3).phase());
        assertEquals(OakParcelProgress.Phase.PICKUP, new OakParcelProgress((short) 4, (short) 3).phase());
        assertEquals(OakParcelProgress.Phase.PICKUP, new OakParcelProgress((short) 4, (short) 0).phase());
        assertEquals(OakParcelProgress.Phase.DELIVER, new OakParcelProgress((short) 4, (short) 1).phase());
        assertEquals(OakParcelProgress.Phase.POKEDEX, new OakParcelProgress((short) 4, (short) 2).phase());
        assertEquals(OakParcelProgress.Phase.POKEDEX, new OakParcelProgress((short) 5, (short) 3).phase());
        for (short stage = 6; stage <= 9; stage++) {
            for (short parcel = 0; parcel <= 3; parcel++) {
                assertEquals(OakParcelProgress.Phase.COMPLETE, new OakParcelProgress(stage, parcel).phase());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new OakParcelProgress((short) 4, (short) 4));
    }

    @Test
    void openingCanFinishDuringTheSameLogin() {
        CharacterManager manager = manager((short) 3, (short) 3);
        assertEquals(OakParcelProgress.Phase.OPENING, OakParcelStory.progress(manager).phase());
        PalletOpeningService.apply(manager, new PalletStoryProgress((short) 4, (short) 0));
        assertEquals(OakParcelProgress.Phase.PICKUP, OakParcelStory.progress(manager).phase());
        manager.getPalletStory().progressLoadFailed = true;
        assertThrows(IllegalStateException.class, () -> OakParcelStory.progress(manager));
        manager.getPalletStory().progressLoadFailed = false;
        manager.getPalletStory().parcelStatus = null;
        assertThrows(IllegalStateException.class, () -> OakParcelStory.progress(manager));
    }

    @Test
    void completedAndNotYetEligiblePlayersStillReachTheShop() {
        CharacterManager manager = manager((short) 6, (short) 2);
        MapData mart = map("viridian_city_mart/ViridianCity_Mart");
        manager.getCurrentMapDatas()[0] = mart;
        NpcEntity clerk = mart.getNpcEntityHashMap().get("npc_0");
        assertTrue(OakParcelStory.isClerk(manager, clerk));
        assertFalse(StoryService.beforeShop(manager, clerk));
        PalletOpeningService.apply(manager, new PalletStoryProgress((short) 2, (short) 3));
        assertFalse(StoryService.beforeShop(manager, clerk));
        assertFalse(OakParcelStory.isClerk(manager, mart.getNpcEntityHashMap().get("npc_1")));
        manager.getCurrentMapDatas()[0] = map("pallet_town_professor_oaks_lab/PalletTown_ProfessorOaksLab");
        assertFalse(OakParcelStory.isClerk(manager, clerk));
    }

    @Test
    void parcelRivalVisibilityIsPerPlayerAndEndsAfterPokedex() {
        MapData lab = map("pallet_town_professor_oaks_lab/PalletTown_ProfessorOaksLab");
        CharacterManager returning = manager((short) 4, (short) 1);
        CharacterManager finished = manager((short) 6, (short) 2);
        returning.getCurrentMapDatas()[0] = lab;
        finished.getCurrentMapDatas()[0] = lab;
        lab.loadArroundEntity(returning.getCharacterSession());
        NpcEntity rival = lab.getNpcEntityHashMap().get("npc_7");
        assertNotNull(PalletStoryNpcs.project(returning, lab, rival));
        assertNull(PalletStoryNpcs.project(finished, lab, rival));
        assertNotNull(PalletStoryNpcs.project(returning, lab, lab.getNpcEntityHashMap().get("npc_8")));
        assertNull(PalletStoryNpcs.project(finished, lab, lab.getNpcEntityHashMap().get("npc_8")));
        assertFalse(rival.isLoad());
        assertTrue(OakParcelStory.isOak(returning, lab.getNpcEntityHashMap().get("npc_3")));
        assertFalse(OakParcelStory.isOak(returning, rival));
    }

    @Test
    void progressApplicationPreservesAssetsAndStarterSelection() {
        CharacterManager manager = manager((short) 4, (short) 1);
        manager.getCharacterData().setMoney(900);
        manager.getCharacterData().getFirstPartnerStatus()[1] = 2;
        new OakParcelProgress((short) 5, (short) 2).apply(manager.getCharacterData());
        assertEquals(5, manager.getCharacterData().getOakLabStatus());
        assertEquals(2, manager.getCharacterData().getOakParcelStatus());
        assertEquals(0, manager.getCharacterData().getFirstPartnerStatus()[0]);
        assertEquals(2, manager.getCharacterData().getFirstPartnerStatus()[1]);
        assertEquals(900, manager.getCharacterData().getMoney());
    }

    @Test
    void nativeTextAndProtectedParcelUseTheConfirmedItemIds() {
        assertEquals(1639049, catalog.text("parcelReceived"));
        assertEquals(1631722, catalog.text("dexReceived"));
        assertEquals(1631923, catalog.text("ballsReceived"));
        assertEquals(5004, OakParcelCatalog.BALL_ITEM_ID);
        assertEquals(5, OakParcelCatalog.BALL_COUNT);
        assertNotNull(ItemManager.getItemData(ItemManager.OAK_PARCEL_ITEM_ID));
        assertTrue(ItemManager.isStoryBound((short) 349));
        assertFalse(ItemManager.isTradeableForExchange((short) 349));
        assertFalse(ItemManager.isStoryBound((short) 5004));
        assertTrue(ItemManager.isTradeableForExchange((short) 5004));
    }

    private static MapData map(String key) {
        String previous = System.getProperty("openmmo.map.npcs.enabled");
        System.setProperty("openmmo.map.npcs.enabled", "true");
        try {
            MapFile file = new MapFile();
            file.setJsonFile(RESOURCE.resolve("map/kanto/" + key + ".json").toFile());
            MapData result = file.parseMapData();
            assertNotNull(result);
            return result;
        } finally {
            if (previous == null) System.clearProperty("openmmo.map.npcs.enabled");
            else System.setProperty("openmmo.map.npcs.enabled", previous);
        }
    }

    private static CharacterManager manager(short stage, short parcel) {
        Session session = new TestSession();
        ScriptManager scripts = new ScriptManager(new String[0]);
        scripts.setOakParcelStory(catalog);
        CharacterManager manager = new CharacterManager.Builder().setCharacterSession(session).setScriptManager(scripts)
                .setSnowflakeIdGenerator(new SnowflakeIdGenerator(1, 1)).build();
        CharacterData character = new CharacterData.Builder().build();
        character.getPlayerEntity().setEntityGameId(600L + stage);
        character.setFirstPartnerStatus(new short[]{0, 3, 3, 3, 3});
        character.setOakLabStatus(stage);
        character.setOakParcelStatus(parcel);
        manager.setCharacterData(character);
        manager.getPalletStory().progress = PalletStoryProgress.from(character);
        manager.getPalletStory().parcelStatus = parcel;
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(manager);
        return manager;
    }

    private static final class TestSession extends Session {
        private final DefaultAttributeMap attributes = new DefaultAttributeMap();
        TestSession() { super(null, null, Side.SERVER, ServerType.GAME); }
        @Override public boolean isActive() { return true; }
        @Override public <T> Attribute<T> attr(AttributeKey<T> key) { return attributes.attr(key); }
    }
}
