package org.pokemmo.gameserver.game.story;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.Unpooled;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.codecs.BattleTeamInfoCodec;
import org.pokemmo.gameserver.game.battle.BattleGenerator;
import org.pokemmo.gameserver.game.battle.TrainerTeam;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapFile;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleInitPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendChatMessagePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendHasEventPacket;
import org.pokemmo.gameserver.script.ScriptManager;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.server.ServerType;
import org.server.Session;
import org.server.Packet;
import org.server.bytes.ByteBufEx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PalletStoryTest {
    private static final Path RESOURCE = Files.isDirectory(Path.of("resource")) ? Path.of("resource") : Path.of("../resource");
    private static PalletStoryCatalog catalog;

    @BeforeAll
    static void resourcesOnly() {
        new PokemonManager(RESOURCE.resolve("pokemon/Pokemon.jsonc").toString(),
                RESOURCE.resolve("pokemon").toString());
        new MoveManager(RESOURCE.resolve("move/Move.bin").toString(), RESOURCE.resolve("move").toString());
        catalog = new PalletStoryCatalog(RESOURCE.resolve("story"));
    }

    @Test
    void completedAndLaterLegacyStagesNeverReplay() {
        for (short stage = 4; stage <= 9; stage++) assertTrue(new PalletStoryProgress(stage, (short) 3).completed());
        for (short stage = 0; stage < 4; stage++) assertFalse(new PalletStoryProgress(stage, (short) 0).completed());
        assertThrows(IllegalArgumentException.class, () -> new PalletStoryProgress((short) 3, (short) 3));
        assertThrows(IllegalArgumentException.class, () -> new PalletStoryProgress((short) -1, (short) 3));
    }

    @Test
    void persistedProgressKeepsOtherRegionsAndParcelUnchanged() {
        CharacterData data = new CharacterData.Builder().build();
        data.setFirstPartnerStatus(new short[]{3, 1, 2, 3, 0});
        data.setOakParcelStatus((short) 2);
        new PalletStoryProgress((short) 3, (short) 1).apply(data);
        assertEquals(new PalletStoryProgress((short) 3, (short) 1), PalletStoryProgress.from(data));
        assertEquals(1, data.getFirstPartnerStatus()[1]);
        assertEquals(2, data.getOakParcelStatus());
    }

    @Test
    void starterFactoryDoesNotWritePartyAndUsesIndependentArrays() {
        CharacterManager manager = manager(100, (short) 2, (short) 3);
        for (int choice = 0; choice < 3; choice++) {
            PokemonData first = PalletStarterFactory.create(manager, catalog.starter(choice), true);
            PokemonData second = PalletStarterFactory.create(manager, catalog.starter(choice), true);
            assertEquals(new int[]{1, 4, 7}[choice], first.getPokemonIndexId());
            assertEquals(100L, first.getTrainerId());
            assertEquals(100L, first.getOriginalTrainerId());
            assertEquals(5, first.getLevel());
            assertTrue(first.getMaxHp() > 0);
            assertEquals(first.getMaxHp(), first.getCurrentHp());
            assertTrue(first.getNormalRibbon()[2]);
            assertNull(manager.getPartyPokemons()[0]);
            first.getMovesPp()[0] = 0;
            assertTrue(second.getMovesPp()[0] > 0);
        }
    }

    @Test
    void twoPlayersSeeDifferentNpcStatesWithoutChangingBaseMap() {
        String old = System.getProperty("openmmo.map.npcs.enabled");
        System.setProperty("openmmo.map.npcs.enabled", "true");
        try {
            MapFile file = new MapFile();
            file.setJsonFile(RESOURCE.resolve("map/kanto/pallet_town_professor_oaks_lab/PalletTown_ProfessorOaksLab.json").toFile());
            MapData lab = file.parseMapData();
            assertNotNull(lab);
            CharacterManager first = manager(200, (short) 2, (short) 3);
            CharacterManager second = manager(201, (short) 4, (short) 0);
            first.getCurrentMapDatas()[0] = lab;
            second.getCurrentMapDatas()[0] = lab;
            lab.loadArroundEntity(first.getCharacterSession());
            NpcEntity ball = lab.getNpcEntityHashMap().get("npc_4");
            assertNotNull(PalletStoryNpcs.project(first, lab, ball));
            assertNull(PalletStoryNpcs.project(second, lab, ball));
            assertFalse(ball.isLoad());
            NpcEntity oak = lab.getNpcEntityHashMap().get("npc_3");
            PalletStoryNpcs.pose(first, oak, 6, 6, 1);
            assertEquals(6, PalletStoryNpcs.project(first, lab, oak).getY());
            assertEquals(3, PalletStoryNpcs.project(second, lab, oak).getY());
            assertEquals(3, oak.getY());
        } finally {
            if (old == null) System.clearProperty("openmmo.map.npcs.enabled");
            else System.setProperty("openmmo.map.npcs.enabled", old);
        }
    }

    @Test
    void oldAndDuplicatedInteractionRepliesDoNotExecuteAgain() {
        CharacterManager manager = manager(300, (short) 2, (short) 3);
        AtomicInteger calls = new AtomicInteger();
        PalletStoryState state = manager.getPalletStory();
        state.expectedReply = 7;
        state.yesNo = true;
        state.continuation = calls::addAndGet;
        PalletStoryScene.reply(manager, (byte) 6, 1);
        assertEquals(0, calls.get());
        PalletStoryScene.reply(manager, (byte) 7, 1);
        PalletStoryScene.reply(manager, (byte) 7, 1);
        assertEquals(1, calls.get());
    }

    @Test
    void nativeTownAndLabRoutesAreReachable() {
        for (String key : new String[]{"pallet_town/PalletTown", "pallet_town_professor_oaks_lab/PalletTown_ProfessorOaksLab"}) {
            MapFile file = new MapFile();
            file.setJsonFile(RESOURCE.resolve("map/kanto/" + key + ".json").toFile());
            MapData map = file.parseMapData();
            assertNotNull(map);
            if (key.endsWith("PalletTown")) {
                assertFalse(PalletStoryScene.npcPath(map, 10, 8, 12, 2).isEmpty());
                assertFalse(PalletStoryScene.npcPath(map, 12, 2, 16, 14).isEmpty());
                assertFalse(PalletStoryScene.npcPath(map, 13, 1, 16, 15).isEmpty());
            } else assertFalse(PalletStoryScene.npcPath(map, 6, 11, 6, 4).isEmpty());
            assertThrows(IllegalStateException.class, () -> PalletStoryScene.npcPath(map, -1, -1, 0, 0));
        }
    }

    @Test
    void trainerHeaderIncludesItemLimitBeforeFactionData() {
        ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
        try {
            new BattleTeamInfoCodec(false).encode(buffer, new TrainerTeam(1, 6, 4, List.of(), 0, 328, 80));
            assertEquals(6, buffer.writerIndex());
            buffer.writeIntLE(0);
            buffer.writeByte(1);
            buffer.writeByte(1);
            assertEquals(2, buffer.readUnsignedByte());
            assertEquals(6, buffer.readUnsignedByte());
            assertEquals(0, buffer.readUnsignedByte());
            assertEquals(328, buffer.readUnsignedShortLE());
            assertEquals(4, buffer.readUnsignedByte());
            assertEquals(0, buffer.readIntLE());
            assertEquals(1, buffer.readUnsignedByte());
            assertEquals(1, buffer.readUnsignedByte());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void encodingFailureDoesNotPublishABattle() {
        CharacterManager manager = manager(400, (short) 3, (short) 0);
        manager.getPartyPokemons()[0] = PalletStarterFactory.create(manager, catalog.starter(0), true);
        manager.getPartyPokemons()[0].setPokemonStatus(null);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> PalletStoryBattle.start(manager));
        assertTrue(error.getMessage().contains("封包编码检查失败"));
        assertNull(manager.getBattleManager());
        assertNull(manager.getPalletStory().battle);
        assertNull(GameSessionPool.getBattleManagerInPool(400));
        assertEquals(3, manager.getCharacterData().getOakLabStatus());
        assertTrue(((TestSession) manager.getCharacterSession()).sent.isEmpty());
    }

    @Test
    void startupExceptionReleasesBattleAndStoryLocksWithoutResettingProgress() {
        CharacterManager manager = manager(401, (short) 3, (short) 0);
        PokemonData starter = PalletStarterFactory.create(manager, catalog.starter(0), true);
        manager.getPartyPokemons()[0] = starter;
        TestSession session = (TestSession) manager.getCharacterSession();
        session.failBattleInit = true;
        manager.getInteractManager().setInteractType(InteractType.STORY);
        PalletOpeningService.guard(manager, session, () -> PalletStoryBattle.start(manager));
        assertNull(manager.getBattleManager());
        assertNull(manager.getPalletStory().battle);
        assertNull(GameSessionPool.getBattleManagerInPool(401));
        assertEquals(InteractType.NONE, manager.getInteractManager().getInteractType());
        assertEquals(3, manager.getCharacterData().getOakLabStatus());
        assertEquals(starter.getPokemonId(), manager.getPartyPokemons()[0].getPokemonId());
    }

    @Test
    void reconnectReadsResetProgressWithoutReplacingPartyOrOtherRegions() {
        CharacterManager manager = manager(500, (short) 4, (short) 0);
        PokemonData starter = PalletStarterFactory.create(manager, catalog.starter(0), true);
        manager.getPartyPokemons()[0] = starter;
        manager.getCharacterData().getFirstPartnerStatus()[1] = 2;
        manager.getCharacterData().setMoney(1234);
        manager.getInteractManager().setInteractType(InteractType.STORY);
        manager.getPalletStory().awaitingMap = true;
        PalletStoryLifecycle.reload(manager, () -> new PalletStoryProgress((short) 0, (short) 3));
        assertEquals(new PalletStoryProgress((short) 0, (short) 3), PalletOpeningService.progress(manager));
        assertEquals(0, manager.getCharacterData().getOakLabStatus());
        assertEquals(3, manager.getCharacterData().getFirstPartnerStatus()[0]);
        assertEquals(2, manager.getCharacterData().getFirstPartnerStatus()[1]);
        assertEquals(1234, manager.getCharacterData().getMoney());
        assertSame(starter, manager.getPartyPokemons()[0]);
        assertEquals(InteractType.NONE, manager.getInteractManager().getInteractType());
        assertFalse(manager.getPalletStory().awaitingMap);
    }

    @Test
    void failedProgressReadCannotFallBackToStaleMemory() {
        CharacterManager manager = manager(501, (short) 4, (short) 0);
        PalletStoryLifecycle.reload(manager, () -> { throw new IllegalStateException("测试：存档查询失败"); });
        assertTrue(manager.getPalletStory().progressLoadFailed);
        assertNull(manager.getPalletStory().progress);
        assertThrows(IllegalStateException.class, () -> PalletOpeningService.progress(manager));
        assertEquals(4, manager.getCharacterData().getOakLabStatus());
        PalletStoryLifecycle.reload(manager, () -> new PalletStoryProgress((short) 0, (short) 3));
        assertFalse(manager.getPalletStory().progressLoadFailed);
        assertEquals(0, PalletOpeningService.progress(manager).stage());
    }

    @Test
    void reconnectDuringBattleDoesNotApplyAnExternalReset() {
        CharacterManager manager = manager(502, (short) 3, (short) 0);
        PokemonData starter = PalletStarterFactory.create(manager, catalog.starter(0), true);
        PokemonData rival = PalletStarterFactory.create(manager, catalog.starter(1), false);
        manager.getPartyPokemons()[0] = starter;
        var battle = BattleGenerator.generatorTrainerRivalBattle(manager.getCharacterSession(), 0, 328, 80,
                new PokemonData[]{starter}, new PokemonData[]{rival});
        manager.setBattleManager(battle);
        manager.getPalletStory().battle = battle;
        AtomicInteger reads = new AtomicInteger();
        PalletStoryLifecycle.reload(manager, () -> {
            reads.incrementAndGet();
            return new PalletStoryProgress((short) 0, (short) 3);
        });
        assertEquals(0, reads.get());
        assertEquals(3, PalletOpeningService.progress(manager).stage());
        assertSame(battle, manager.getBattleManager());
    }

    @Test
    void initialMapLoadRecognizesOnlyTheUnfinishedTownTrigger() {
        CharacterManager manager = manager(503, (short) 0, (short) 3);
        MapFile file = new MapFile();
        file.setJsonFile(RESOURCE.resolve("map/kanto/pallet_town/PalletTown.json").toFile());
        MapData town = file.parseMapData();
        assertNotNull(town);
        manager.getCurrentMapDatas()[0] = town;
        manager.getCharacterData().getPlayerEntity().setX((short) 12);
        manager.getCharacterData().getPlayerEntity().setY((short) 1);
        PalletStoryProgress initial = PalletOpeningService.progress(manager);
        assertTrue(PalletOpeningService.shouldStartOnMapReady(manager, initial));
        assertFalse(PalletOpeningService.shouldStartOnMapReady(manager, new PalletStoryProgress((short) 4, (short) 0)));
        assertFalse(PalletOpeningService.shouldStartOnMapReady(manager, new PalletStoryProgress((short) 0, (short) 0)));
        manager.getInteractManager().setInteractType(InteractType.SHOP);
        assertFalse(PalletOpeningService.shouldStartOnMapReady(manager, initial));
        manager.getInteractManager().setInteractType(InteractType.NONE);
        manager.getCharacterData().getPlayerEntity().setY((short) 2);
        assertFalse(PalletOpeningService.shouldStartOnMapReady(manager, initial));
        manager.getCharacterData().getPlayerEntity().setX((short) 13);
        manager.getCharacterData().getPlayerEntity().setY((short) 1);
        assertTrue(PalletOpeningService.shouldStartOnMapReady(manager, initial));
        manager.getCurrentMapDatas()[0] = null;
        assertFalse(PalletOpeningService.shouldStartOnMapReady(manager, initial));
    }

    @Test
    void partialResetRequiresBothCheckpointFieldsWithoutChangingLegacyData() {
        for (short stage = 0; stage < PalletStoryProgress.RIVAL; stage++) {
            PalletStoryProgress unclaimed = new PalletStoryProgress(stage, (short) 3);
            assertDoesNotThrow(unclaimed::validateOpeningCheckpoint);
            for (short choice = 0; choice < 3; choice++) {
                PalletStoryProgress partialReset = new PalletStoryProgress(stage, choice);
                IllegalStateException error = assertThrows(IllegalStateException.class, partialReset::validateOpeningCheckpoint);
                assertTrue(error.getMessage().contains("oak_lab_status=" + stage));
                assertTrue(error.getMessage().contains("first_partner_status[1]=" + choice));
                assertEquals(stage, partialReset.stage());
                assertEquals(choice, partialReset.starter());
            }
        }
        for (short choice = 0; choice < 3; choice++) {
            assertDoesNotThrow(new PalletStoryProgress((short) 3, choice)::validateOpeningCheckpoint);
        }
        for (short stage = 4; stage <= 9; stage++) {
            for (short choice = 0; choice <= 3; choice++) {
                assertDoesNotThrow(new PalletStoryProgress(stage, choice)::validateOpeningCheckpoint);
            }
        }
    }

    @Test
    void conflictingStepAndMapEntryDoNotStartSceneOrResetAssets() throws Exception {
        for (boolean mapReady : new boolean[]{false, true}) {
            CharacterManager manager = manager(mapReady ? 505 : 504, (short) 0, (short) 0);
            MapFile file = new MapFile();
            file.setJsonFile(RESOURCE.resolve("map/kanto/pallet_town/PalletTown.json").toFile());
            MapData town = file.parseMapData();
            assertNotNull(town);
            manager.getCurrentMapDatas()[0] = town;
            manager.getCharacterData().getPlayerEntity().setX((short) 12);
            manager.getCharacterData().getPlayerEntity().setY((short) 1);
            PokemonData existing = PalletStarterFactory.create(manager, catalog.starter(0), true);
            manager.getPartyPokemons()[0] = existing;
            manager.getCharacterData().setMoney(1234);
            if (mapReady) PalletOpeningService.onMapReady(manager);
            else assertTrue(PalletOpeningService.onStep(manager, 12, 1));
            assertEquals(InteractType.NONE, manager.getInteractManager().getInteractType());
            assertNull(manager.getPalletStory().continuation);
            assertNull(manager.getPalletStory().timer);
            assertEquals(0, manager.getCharacterData().getOakLabStatus());
            assertEquals(0, manager.getCharacterData().getFirstPartnerStatus()[0]);
            assertEquals(1234, manager.getCharacterData().getMoney());
            assertSame(existing, manager.getPartyPokemons()[0]);
            boolean conflictNotice = false;
            ByteBufEx buffer = new ByteBufEx(Unpooled.buffer());
            try {
                for (Packet packet : ((TestSession) manager.getCharacterSession()).sent) {
                    buffer.clear();
                    if (packet instanceof SendHasEventPacket event) {
                        event.encode(buffer);
                        assertFalse(buffer.readBoolean());
                    } else if (packet instanceof SendChatMessagePacket chat) {
                        chat.encode(buffer);
                        buffer.readByte();
                        String notice = buffer.readUtf16LE();
                        conflictNotice |= notice.contains("oak_lab_status=0") && notice.contains("first_partner_status[1]=0");
                    }
                }
            } finally {
                buffer.release();
            }
            assertTrue(conflictNotice);
        }
    }

    private static CharacterManager manager(long id, short stage, short starter) {
        TestSession session = new TestSession();
        ScriptManager scripts = new ScriptManager(new String[0]);
        scripts.setPalletStory(catalog);
        CharacterManager manager = new CharacterManager.Builder().setScriptManager(scripts)
                .setCharacterSession(session).setSnowflakeIdGenerator(new SnowflakeIdGenerator(1, 1)).build();
        CharacterData data = new CharacterData.Builder().build();
        data.getPlayerEntity().setEntityGameId(id);
        data.getPlayerEntity().setPlayerName("StoryTest");
        data.setFirstPartnerStatus(new short[]{starter, 3, 3, 3, 3});
        data.setOakLabStatus(stage);
        manager.setCharacterData(data);
        manager.getPalletStory().progress = PalletStoryProgress.from(data);
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(manager);
        return manager;
    }

    private static final class TestSession extends Session {
        private final DefaultAttributeMap attributes = new DefaultAttributeMap();
        private final List<Packet> sent = new ArrayList<>();
        private boolean failBattleInit;
        TestSession() { super(null, null, Side.SERVER, ServerType.GAME); }
        @Override public boolean isActive() { return true; }
        @Override public <T> Attribute<T> attr(AttributeKey<T> key) { return attributes.attr(key); }
        @Override public void send(Packet... packets) {
            for (Packet packet : packets) {
                if (failBattleInit && packet instanceof SendBattleInitPacket) {
                    throw new IllegalStateException("测试注入：战斗初始化发送失败");
                }
                sent.add(packet);
            }
        }
    }
}
