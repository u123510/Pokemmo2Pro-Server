package org.pokemmo.gameserver.game.story;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.entity.SportType;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.script.InteractScript;
import org.pokemmo.gameserver.protocol.packets.s2c.SendEntitySportPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendHasEventPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInteractPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPlayMusicPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetEntityPosPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShowPokemonNameplateWidgetPacket;
import org.server.Session;

/** Native presentation with reply/animation continuations, never a blocking sleep. */
public final class PalletStoryScene {
    private static final ScheduledExecutorService CLOCK = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "pallet-story");
        thread.setDaemon(true);
        return thread;
    });

    private PalletStoryScene() { }

    static void begin(CharacterManager manager) {
        manager.getInteractManager().setInteractType(InteractType.STORY);
        manager.getCharacterSession().send(new SendHasEventPacket(true));
    }

    static void say(CharacterManager manager, long actor, String text, Runnable next) {
        prompt(manager, actor, manager.getScriptManager().getPalletStory().text(text), false, ignored -> next.run());
    }

    static void sayText(CharacterManager manager, long actor, int text, Runnable next) {
        prompt(manager, actor, text, false, ignored -> next.run());
    }

    static void prompt(CharacterManager manager, long actor, int offset, boolean yesNo, IntConsumer next) {
        PalletStoryState state = manager.getPalletStory();
        state.cancelWait();
        manager.getInteractManager().addInteractTimes();
        state.expectedReply = manager.getInteractManager().getInteractTimes();
        state.yesNo = yesNo;
        state.continuation = next;
        InteractScript text = new InteractScript("Story", yesNo ? GameInteractionType.MSG_YESNO
                : GameInteractionType.MSG_NOCLOSE, offset, 0, 0);
        manager.getInteractManager().setCurrentInteractScript(text);
        manager.getInteractManager().setLastInteractorEntityId(actor);
        manager.getCharacterSession().send(new SendInteractPacket(actor, state.expectedReply, text));
        schedule(manager, 120_000, () -> end(manager));
    }

    static void reply(CharacterManager manager, byte sequence, int choice) {
        PalletStoryState state = manager.getPalletStory();
        if (state.continuation == null || sequence != state.expectedReply) return;
        if (state.yesNo && choice != 0 && choice != 1) {
            end(manager);
            return;
        }
        if (!state.yesNo && choice != 0) return;
        IntConsumer next = state.continuation;
        state.cancelWait();
        next.accept(choice);
    }

    static void later(CharacterManager manager, long millis, Runnable next) {
        manager.getPalletStory().cancelWait();
        schedule(manager, millis, next);
    }

    private static void schedule(CharacterManager manager, long millis, Runnable next) {
        PalletStoryState state = manager.getPalletStory();
        long generation = state.generation;
        Session session = manager.getCharacterSession();
        state.timer = CLOCK.schedule(() -> PalletOpeningService.guard(manager, session, () -> {
            if (generation == state.generation && manager.getInteractManager().getInteractType() == InteractType.STORY) {
                state.timer = null;
                next.run();
            }
        }), millis, TimeUnit.MILLISECONDS);
    }

    static void closeDialog(CharacterManager manager) {
        manager.getCharacterSession().send(new SendInteractPacket(-1,
                manager.getInteractManager().getInteractTimes(), new InteractScript("Story",
                GameInteractionType.CLOSE, 0, 0, 0)));
    }

    static void end(CharacterManager manager) {
        PalletStoryState state = manager.getPalletStory();
        state.cancelWait();
        state.clearStoryContext();
        state.nurseInteraction = false;
        state.actors = Map.of();
        state.awaitingMap = false;
        manager.getInteractManager().setCurrentInteractScript(null);
        manager.getInteractManager().clearLastInteractorEntityId();
        if (manager.getInteractManager().getInteractType() == InteractType.STORY) {
            manager.getInteractManager().setInteractType(InteractType.NONE);
        }
        Session session = manager.getCharacterSession();
        if (session != null && session.isActive()) {
            closeDialog(manager);
            session.send(new SendShowPokemonNameplateWidgetPacket((byte) 0, (short) -1, false),
                    new SendHasEventPacket(false), new SendPlayMusicPacket((byte) 0, (short) 0, false));
            PalletStoryNpcs.refresh(manager);
        }
    }

    static void walkPlayer(CharacterManager manager, int x, int y, Runnable next) {
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        // The existing character pathfinder is read-only for Self and preserves map collision rules.
        List<SportType> path = manager.AutoMove(player.getEntityGameId(), false, player.getRegionIndexId(),
                player.getMapHeaderIdOrGbaMapGroupId(), player.getGbaMapId(), (short) x, (short) y, player.getZ());
        if (path == null || path.size() > 255) throw new IllegalStateException("剧情玩家移动路径不可达");
        if (path.isEmpty()) { next.run(); return; }
        SendEntitySportPacket animation = new SendEntitySportPacket(player.getEntityGameId(), false, path);
        manager.getCharacterSession().send(animation);
        PlayerVisibilityService.broadcast(manager, animation, false);
        later(manager, SportType.getActionTimeConsuming(path), () -> {
            player.setX((short) x);
            player.setY((short) y);
            player.setToward(path.get(path.size() - 1).getMoveToward());
            manager.getCharacterSession().send(new SendSetEntityPosPacket(player));
            manager.broadcastPlayerPosition();
            next.run();
        });
    }

    static void walkNpc(CharacterManager manager, int index, int x, int y, Runnable next) {
        NpcEntity npc = PalletStoryNpcs.actor(manager, index);
        List<SportType> path = npcPath(manager.getCurrentMapDatas()[0], npc.getX(), npc.getY(), x, y);
        if (path.isEmpty()) { next.run(); return; }
        manager.getCharacterSession().send(new SendEntitySportPacket(npc.getEntityGameId(), false, path));
        later(manager, SportType.getActionTimeConsuming(path), () -> {
            PalletStoryNpcs.pose(manager, npc, x, y, path.get(path.size() - 1).getMoveToward());
            next.run();
        });
    }

    static void guide(CharacterManager manager, int index, int npcX, int npcY, int playerX, int playerY, Runnable next) {
        NpcEntity npc = PalletStoryNpcs.actor(manager, index);
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        MapData map = manager.getCurrentMapDatas()[0];
        List<SportType> npcSteps = npcPath(map, npc.getX(), npc.getY(), npcX, npcY);
        List<SportType> playerSteps = manager.AutoMove(player.getEntityGameId(), false, player.getRegionIndexId(),
                player.getMapHeaderIdOrGbaMapGroupId(), player.getGbaMapId(), (short) playerX, (short) playerY, player.getZ());
        if (playerSteps == null || playerSteps.size() > 255) throw new IllegalStateException("护送路线不可达");
        if (!npcSteps.isEmpty()) manager.getCharacterSession().send(new SendEntitySportPacket(npc.getEntityGameId(), false, npcSteps));
        if (!playerSteps.isEmpty()) {
            SendEntitySportPacket animation = new SendEntitySportPacket(player.getEntityGameId(), false, playerSteps);
            manager.getCharacterSession().send(animation);
            PlayerVisibilityService.broadcast(manager, animation, false);
        }
        later(manager, Math.max(SportType.getActionTimeConsuming(npcSteps), SportType.getActionTimeConsuming(playerSteps)), () -> {
            PalletStoryNpcs.pose(manager, npc, npcX, npcY, 1);
            player.setX((short) playerX);
            player.setY((short) playerY);
            player.setToward((byte) 1);
            manager.broadcastPlayerPosition();
            next.run();
        });
    }

    /** Bounded tile routing for private actors, independent of the shared NPC mutation in AutoMove. */
    static List<SportType> npcPath(MapData map, int x, int y, int targetX, int targetY) {
        int width = map.getMapWidth();
        int height = map.getMapHeight();
        if (width <= 0 || height <= 0 || (long) width * height > 65536
                || !map.checkIsWalkable(x, y) || !map.checkIsWalkable(targetX, targetY)) {
            throw new IllegalStateException("剧情 NPC 起点或终点不可通行");
        }
        int start = y * width + x;
        int target = targetY * width + targetX;
        int[] previous = new int[width * height];
        Arrays.fill(previous, -1);
        previous[start] = start;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int[][] offsets = {{0, 1}, {0, -1}, {-1, 0}, {1, 0}};
        while (!queue.isEmpty() && previous[target] == -1) {
            int current = queue.remove();
            for (int[] delta : offsets) {
                int nx = current % width + delta[0];
                int ny = current / width + delta[1];
                if (!map.checkIsWalkable(nx, ny)) continue;
                int position = ny * width + nx;
                if (previous[position] != -1) continue;
                previous[position] = current;
                queue.add(position);
            }
        }
        if (previous[target] < 0) throw new IllegalStateException("剧情 NPC 无可用路径");
        ArrayDeque<SportType> steps = new ArrayDeque<>();
        for (int pos = target; pos != start; pos = previous[pos]) {
            int dx = pos % width - previous[pos] % width;
            int dy = pos / width - previous[pos] / width;
            steps.addFirst(dx == 1 ? SportType.WALK_RIGHT : dx == -1 ? SportType.WALK_LEFT
                    : dy == 1 ? SportType.WALK_DOWN : SportType.WALK_UP);
        }
        if (steps.size() > 255) throw new IllegalStateException("剧情 NPC 路径过长");
        return new ArrayList<>(steps);
    }
}
