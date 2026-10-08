package org.pokemmo.gameserver.game.character;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import org.junit.jupiter.api.Test;
import org.pokemmo.gameserver.game.entity.EntityNameplateType;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.entity.SportType;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.NdsMapData;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.game.region.RegionType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEntitySportPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadPlayerPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemoveEntityPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetFollowPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerTransportationPacket;
import org.pokemmo.gameserver.script.ScriptManager;
import org.server.Packet;
import org.server.ServerType;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVisibilityTest {
    private final ScriptManager scripts = new ScriptManager(new String[0]);
    private final MapData map = map(3);
    private final RegionData region = new RegionData(RegionType.KANTO);

    PlayerVisibilityTest() {
        region.getRegionMaps().put(3, map);
        scripts.getRegionDatas()[0] = region;
    }

    @Test
    void onlySelfAndGmOrHigherCanSeeHiddenPlayers() {
        for (PermissionType permission : PermissionType.values()) {
            assertTrue(PlayerVisibilityService.allowed(false, permission, false));
            assertTrue(PlayerVisibilityService.allowed(true, permission, true));
            assertEquals(permission.getType() >= PermissionType.GM.getType(),
                    PlayerVisibilityService.allowed(true, permission, false));
        }
        assertFalse(PlayerVisibilityService.allowed(true, null, false));
    }

    @Test
    void hideToggleRemovesOnlyOrdinaryViewersAndNeverSelf() throws Exception {
        Actor gm = actor(100, PermissionType.GM);
        Actor ordinary = actor(101, PermissionType.NORMAL);
        Actor admin = actor(102, PermissionType.ADM);
        assertTrue(PlayerVisibilityService.toggle(gm.session, gm.manager));
        assertEquals(2, ordinary.session.sent.size());
        assertInstanceOf(SendSetFollowPokemonPacket.class, ordinary.session.sent.get(0));
        assertInstanceOf(SendRemoveEntityPacket.class, ordinary.session.sent.get(1));
        assertInstanceOf(SendLoadPlayerPacket.class, admin.session.sent.get(0));
        assertTrue(gm.session.sent.isEmpty());
        ByteBufEx bytes = new ByteBufEx(Unpooled.buffer());
        try {
            ordinary.session.sent.get(0).encode(bytes);
            assertEquals(100L, bytes.readLongLE());
            assertEquals(0, bytes.readUnsignedShortLE());
            assertEquals(0, bytes.readUnsignedByte());
            assertTrue(bytes.readBoolean());
        } finally {
            bytes.release();
        }
        ordinary.session.sent.clear();
        assertFalse(PlayerVisibilityService.toggle(gm.session, gm.manager));
        assertInstanceOf(SendLoadPlayerPacket.class, ordinary.session.sent.get(0));
        assertInstanceOf(SendUpdatePlayerTransportationPacket.class, ordinary.session.sent.get(1));
        assertEquals(25, gm.manager.getCharacterData().getPlayerEntity().getFollowPokemonIndexId());
        assertEquals(PermissionType.GM, gm.manager.getCharacterData().getPlayerEntity().getPermission());
    }

    @Test
    void hiddenSubjectCannotLeakThroughAnyGenericDeltaOrFullLoad() {
        Actor gm = actor(200, PermissionType.GM);
        Actor ordinary = actor(201, PermissionType.NORMAL);
        Actor admin = actor(202, PermissionType.GM);
        gm.manager.getPlayerVisibility().toggle(200);
        Packet move = new SendEntitySportPacket(200, false, List.of(SportType.WALK_DOWN));
        PlayerVisibilityService.sendPlayer(gm.manager, ordinary.session);
        PlayerVisibilityService.sendIfVisible(gm.manager, ordinary.session, move);
        PlayerVisibilityService.sendIfVisible(gm.manager, admin.session, move);
        PlayerVisibilityService.sendIfVisible(gm.manager, gm.session, move);
        assertTrue(ordinary.session.sent.isEmpty());
        assertEquals(1, admin.session.sent.size());
        assertEquals(1, gm.session.sent.size());
    }

    @Test
    void mapSyncIsDirectionalSoHiddenGmStillSeesOrdinaryPlayers() {
        Actor gm = actor(300, PermissionType.GM);
        Actor ordinary = actor(301, PermissionType.NORMAL);
        gm.manager.getPlayerVisibility().toggle(300);
        ordinary.manager.synchronizeVisiblePlayersAfterMapLoad();
        assertTrue(ordinary.session.sent.isEmpty());
        assertInstanceOf(SendLoadPlayerPacket.class, gm.session.sent.get(0));
        gm.session.sent.clear();
        gm.manager.synchronizeVisiblePlayersAfterMapLoad();
        assertTrue(ordinary.session.sent.isEmpty());
        assertInstanceOf(SendLoadPlayerPacket.class, gm.session.sent.get(0));
    }

    @Test
    void reverseConnectedMapViewersAreRemovedEvenWithoutOutgoingConnection() {
        Actor gm = actor(400, PermissionType.GM);
        Actor connectedViewer = actor(401, PermissionType.NORMAL);
        MapData other = map(4);
        region.getRegionMaps().put(4, other);
        map.removePlayerSession(401);
        other.addPlayerSession(401, connectedViewer.session);
        connectedViewer.manager.getCurrentMapDatas()[0] = other;
        connectedViewer.manager.getCurrentMapDatas()[1] = map;
        connectedViewer.manager.getCharacterData().getPlayerEntity().setGbaMapId((byte) 4);
        assertTrue(PlayerVisibilityService.toggle(gm.session, gm.manager));
        assertInstanceOf(SendRemoveEntityPacket.class, connectedViewer.session.sent.get(1));
    }

    @Test
    void movementTowardPositionAndTransportationStayFiltered() {
        Actor gm = actor(500, PermissionType.GM);
        Actor ordinary = actor(501, PermissionType.NORMAL);
        gm.manager.getPlayerVisibility().toggle(500);
        gm.manager.broadcastPlayerMovement(false);
        gm.manager.broadcastPlayerToward();
        gm.manager.broadcastPlayerPosition();
        gm.manager.broadcastPlayerTransportation();
        PlayerVisibilityService.broadcast(gm.manager, new SendSetFollowPokemonPacket(500, 25, 0, false), true);
        assertTrue(ordinary.session.sent.isEmpty());
        assertFalse(gm.session.sent.isEmpty());
    }

    @Test
    void reconnectPreservesExistingContextButNewContextIsVisible() {
        Actor gm = actor(600, PermissionType.GM);
        gm.manager.getPlayerVisibility().toggle(600);
        RecordingSession replacement = new RecordingSession();
        replacement.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(gm.manager);
        gm.manager.setCharacterSession(replacement);
        assertTrue(PlayerVisibilityService.isHidden(gm.manager));
        PlayerVisibilityService.sendPlayer(gm.manager, gm.session);
        assertTrue(gm.session.sent.isEmpty());
        assertFalse(PlayerVisibilityService.isHidden(actor(600, PermissionType.GM).manager));
        PlayerVisibilityState state = new PlayerVisibilityState();
        state.toggle(600);
        assertFalse(state.isHidden(601));
    }

    @Test
    void rejectsUnprivilegedBusyAndLoadingActorsWithoutChangingState() {
        Actor normal = actor(700, PermissionType.NORMAL);
        assertThrows(IllegalArgumentException.class, () -> PlayerVisibilityService.toggle(normal.session, normal.manager));
        Actor gm = actor(701, PermissionType.GM);
        gm.manager.getInteractManager().setInteractType(InteractType.SHOP);
        assertThrows(IllegalArgumentException.class, () -> PlayerVisibilityService.toggle(gm.session, gm.manager));
        gm.manager.getInteractManager().setInteractType(InteractType.NONE);
        gm.manager.setMapLoadFuture(new CompletableFuture<>());
        assertThrows(IllegalArgumentException.class, () -> PlayerVisibilityService.toggle(gm.session, gm.manager));
        assertFalse(PlayerVisibilityService.isHidden(gm.manager));
    }

    @Test
    void hideWaitsForInFlightLoadAndRemovalIsSentLast() throws Exception {
        Actor gm = actor(800, PermissionType.GM);
        Actor ordinary = actor(801, PermissionType.NORMAL);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch toggleStarted = new CountDownLatch(1);
        ordinary.session.beforeSend = packets -> {
            if (packets[0] instanceof SendLoadPlayerPacket) {
                sending.countDown();
                await(release);
            }
        };
        var workers = Executors.newFixedThreadPool(2);
        try {
            var load = workers.submit(() -> PlayerVisibilityService.sendPlayer(gm.manager, ordinary.session));
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            var hide = workers.submit(() -> {
                toggleStarted.countDown();
                return PlayerVisibilityService.toggle(gm.session, gm.manager);
            });
            assertTrue(toggleStarted.await(5, TimeUnit.SECONDS));
            assertFalse(hide.isDone());
            release.countDown();
            load.get(5, TimeUnit.SECONDS);
            assertTrue(hide.get(5, TimeUnit.SECONDS));
            assertInstanceOf(SendRemoveEntityPacket.class, ordinary.session.sent.get(ordinary.session.sent.size() - 1));
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void removalNeverTargetsSelfAndMutualVisibilityIsNotSymmetricWhenHidden() {
        Actor gm = actor(900, PermissionType.GM);
        Actor ordinary = actor(901, PermissionType.NORMAL);
        gm.manager.getPlayerVisibility().toggle(900);
        assertTrue(PlayerVisibilityService.canSee(ordinary.manager, gm.manager));
        assertFalse(PlayerVisibilityService.canSee(gm.manager, ordinary.manager));
        assertFalse(PlayerVisibilityService.mutuallyVisible(gm.manager, ordinary.manager));
        PlayerVisibilityService.removePlayer(gm.manager, gm.session);
        assertTrue(gm.session.sent.isEmpty());
    }

    private Actor actor(long id, PermissionType permission) {
        RecordingSession session = new RecordingSession();
        CharacterManager manager = new CharacterManager(null, session, scripts, null, null, null);
        CharacterData data = new CharacterData.Builder().build();
        data.setPlayerEntity(new PlayerEntity(id, 0, 5, 3, 4, 3, 2, 0, 0, "test" + id,
                1, EntityNameplateType.NONE, permission, -1, -1, 25, 0, ""));
        manager.setCharacterData(data);
        manager.getCurrentMapDatas()[0] = map;
        manager.setMapLoadFuture(CompletableFuture.completedFuture(true));
        session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).set(manager);
        map.addPlayerSession(id, session);
        return new Actor(manager, session);
    }

    private static MapData map(int id) {
        return new NdsMapData(0, 5, id, 20, 20, null, null, null, true, false, false);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("测试同步超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private record Actor(CharacterManager manager, RecordingSession session) {
    }

    private static final class RecordingSession extends Session {
        private final DefaultAttributeMap attributes = new DefaultAttributeMap();
        private final List<Packet> sent = new ArrayList<>();
        private java.util.function.Consumer<Packet[]> beforeSend = packets -> { };

        private RecordingSession() {
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

        @Override
        public synchronized void send(Packet... packets) {
            beforeSend.accept(packets);
            sent.addAll(Arrays.asList(packets));
        }
    }
}
