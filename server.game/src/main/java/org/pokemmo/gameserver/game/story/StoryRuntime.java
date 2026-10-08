package org.pokemmo.gameserver.game.story;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.FactionResultType;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.script.ScriptManager;
import org.server.Session;

/** Routes and evaluates the data-driven story programs for one character. */
@Slf4j
final class StoryRuntime {
    private StoryRuntime() {
    }

    static boolean enabled(CharacterManager manager) {
        StoryCatalog catalog = catalog(manager);
        return catalog != null && catalog.hasEnabledProgram();
    }

    static void onLogin(CharacterManager manager) {
        if (!enabled(manager)) {
            return;
        }
        PalletStoryLifecycle.onLogin(manager);
        OakParcelStory.onLogin(manager);
        ViridianCatchStory.onLogin(manager);
    }

    static void onMapReady(CharacterManager manager) {
        if (!enabled(manager) || manager.getPalletStory().progressLoadFailed || !idleOrStory(manager)) {
            return;
        }
        try {
            validateOpeningCheckpoint(manager);
            manager.getPalletStory().awaitingMap = false;
            runFirst(manager, "MAP_READY", null, manager.getCharacterData().getPlayerEntity().getX(),
                    manager.getCharacterData().getPlayerEntity().getY(), false);
            if (idleOrStory(manager)) {
                runFirst(manager, "COORDINATE", null, manager.getCharacterData().getPlayerEntity().getX(),
                        manager.getCharacterData().getPlayerEntity().getY(), false);
            }
        } catch (RuntimeException exception) {
            PalletOpeningService.report(manager, exception);
        }
    }

    static boolean onStep(CharacterManager manager, int x, int y) {
        if (!enabled(manager) || manager.getPalletStory().progressLoadFailed || !idleOrStory(manager)) {
            return false;
        }
        try {
            validateOpeningCheckpoint(manager);
        } catch (RuntimeException exception) {
            PalletOpeningService.report(manager, exception);
            return true;
        }
        return runFirst(manager, "COORDINATE", null, x, y, false);
    }

    static boolean beforeShop(CharacterManager manager, NpcEntity npc) {
        if (manager.getPalletStory().progressLoadFailed) return false;
        return runFirst(manager, "NPC", npc, 0, 0, true);
    }

    static boolean onNpc(CharacterManager manager, NpcEntity npc) {
        if (manager.getPalletStory().progressLoadFailed) return false;
        try {
            validateOpeningCheckpoint(manager);
        } catch (RuntimeException exception) {
            PalletOpeningService.report(manager, exception);
            return true;
        }
        return runFirst(manager, "NPC", npc, 0, 0, false);
    }

    static void onReply(CharacterManager manager, Session session, byte sequence, byte choice) {
        PalletOpeningService.guard(manager, session, () ->
                PalletStoryScene.reply(manager, sequence, Byte.toUnsignedInt(choice)));
    }

    static void onDisconnect(CharacterManager manager, Session session) {
        PalletStoryLifecycle.onDisconnect(manager, session);
    }

    static void onBattleFinished(CharacterManager manager, BattleManager battle) {
        String battleId = manager.getPalletStory().storyBattleId;
        if (battleId != null && manager.getBattleManager() == battle) {
            byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
            if (faction >= 0 && faction < battle.debutFactions.size()) {
                manager.getPalletStory().storyBattleResult =
                        battle.debutFactions.get(faction).getFactionStatType().name();
            }
        }
        if ("OPENING_RIVAL".equals(battleId)) {
            PalletStoryBattle.settle(manager, battle);
        } else if ("CAPTURE_TUTORIAL".equals(battleId)) {
            ViridianCatchStory.settle(manager, battle);
        }
    }

    static boolean onFinishReply(CharacterManager manager) {
        String battleId = manager.getPalletStory().storyBattleId;
        if (battleId == null) {
            return false;
        }
        if ("OPENING_RIVAL".equals(battleId)) {
            if (!PalletStoryBattle.finishBattle(manager)) {
                return true;
            }
            StoryProgram program = activeProgram(manager);
            String finishNode = manager.getPalletStory().storyBattleFinishNode;
            manager.getPalletStory().storyBattleId = null;
            manager.getPalletStory().storyBattleFinishNode = null;
            if (program != null) {
                PalletStoryScene.begin(manager);
                StoryActionExecutor.run(manager, program, finishNode);
            }
            return true;
        }
        if ("CAPTURE_TUTORIAL".equals(battleId)) {
            if (!ViridianCatchStory.finishBattle(manager)) {
                if (manager.getPalletStory().viridianCatchBattle == null) {
                    manager.getPalletStory().storyBattleId = null;
                    manager.getPalletStory().storyBattleFinishNode = null;
                }
                return false;
            }
            StoryProgram program = activeProgram(manager);
            String finishNode = manager.getPalletStory().storyBattleFinishNode;
            manager.getPalletStory().storyBattleId = null;
            manager.getPalletStory().storyBattleFinishNode = null;
            if (program != null) {
                PalletStoryScene.begin(manager);
                StoryActionExecutor.run(manager, program, finishNode);
            }
            return true;
        }
        if (battleId.startsWith("EARLY_") || battleId.startsWith("KANTO_")) {
            if (!"VICTORY".equals(manager.getPalletStory().storyBattleResult)) {
                manager.getPalletStory().storyBattleId = null;
                manager.getPalletStory().storyBattleFinishNode = null;
                manager.getPalletStory().storyBattleResult = null;
                return false;
            }
            manager.setBattleManager(null);
            StoryProgram program = activeProgram(manager);
            String finishNode = manager.getPalletStory().storyBattleFinishNode;
            manager.getPalletStory().storyBattleId = null;
            manager.getPalletStory().storyBattleFinishNode = null;
            manager.getPalletStory().storyBattleResult = null;
            if (program != null) {
                PalletStoryScene.begin(manager);
                StoryActionExecutor.run(manager, program, finishNode);
            }
            return true;
        }
        return false;
    }

    static StoryCatalog catalog(CharacterManager manager) {
        ScriptManager scripts = manager == null ? null : manager.getScriptManager();
        return scripts == null ? null : scripts.getStoryCatalog();
    }

    static StoryProgram activeProgram(CharacterManager manager) {
        String id = manager.getPalletStory().activeStoryId;
        return id == null || catalog(manager) == null ? null : catalog(manager).get(id);
    }

    static boolean matchesCondition(CharacterManager manager, JsonObject condition) {
        String type = StoryProgram.string(condition, "type");
        int value = StoryProgram.integer(condition, "value", 0);
        PalletStoryProgress opening = PalletOpeningService.progress(manager);
        return switch (type == null ? "" : type) {
            case "STORY_STAGE_EQUALS" -> opening.stage() == value;
            case "STORY_STAGE_AT_LEAST" -> opening.stage() >= value;
            case "STARTER_EQUALS" -> opening.starter() == value;
            case "SEX_EQUALS" -> manager.getCharacterData().getPlayerEntity().getSex() == value;
            case "PARCEL_PHASE_EQUALS" -> OakParcelStory.progress(manager).phase().name()
                    .equals(StoryProgram.string(condition, "value"));
            case "PARCEL_NEEDS_PICKUP" -> {
                OakParcelProgress progress = OakParcelStory.progress(manager);
                boolean missing = !manager.getCharacterService().getOakParcelStore().hasParcel(
                        manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager));
                yield progress.phase() == OakParcelProgress.Phase.PICKUP
                        || (progress.phase() == OakParcelProgress.Phase.DELIVER && missing);
            }
            case "CAPTURE_TUTORIAL_COMPLETE" ->
                    Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete);
            case "CAPTURE_TUTORIAL_INCOMPLETE" ->
                    !Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete);
            case "HAS_CAPTURE_BALL" -> manager.getCharacterService().getViridianCatchStore().hasCaptureBall(
                    manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager));
            case "NO_CAPTURE_BALL" -> !manager.getCharacterService().getViridianCatchStore().hasCaptureBall(
                    manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager));
            case "HAS_ITEM" -> {
                var inventory = manager.getCharacterService().getInventory();
                yield inventory != null && manager.getCharacterService()
                        .getOwnedItemByIndex(PalletOpeningService.characterId(manager), (short) value) != null;
            }
            case "NO_ITEM" -> {
                var inventory = manager.getCharacterService().getInventory();
                yield inventory == null || manager.getCharacterService()
                        .getOwnedItemByIndex(PalletOpeningService.characterId(manager), (short) value) == null;
            }
            case "STORY_BIT_SET" -> storyBit(manager, value);
            case "STORY_BIT_CLEAR" -> !storyBit(manager, value);
            case "EXTRA_STORY_BIT_SET" -> extraStoryBit(manager, value);
            case "EXTRA_STORY_BIT_CLEAR" -> !extraStoryBit(manager, value);
            case "EVENT_FLAG_SET" -> manager.getPalletStory().hasEventFlag(
                    StoryProgram.string(condition, "flag"));
            case "EVENT_FLAG_CLEAR" -> !manager.getPalletStory().hasEventFlag(
                    StoryProgram.string(condition, "flag"));
            case "BADGE_SET" -> badgeBit(manager, value);
            case "BADGE_CLEAR" -> !badgeBit(manager, value);
            case "CHAMPION_SET" -> manager.getCharacterData().getChampionFlag()[0];
            case "CHAMPION_CLEAR" -> !manager.getCharacterData().getChampionFlag()[0];
            case "ELITE_STAGE_EQUALS" -> manager.getPalletStory().eliteFourStage == value;
            case "INTERACTION_IDLE" -> idle(manager);
            case "NOT_IN_BATTLE" -> manager.getBattleManager() == null;
            case "NOT_IN_TRADE" -> !TradeManager.isInTrade(manager);
            default -> throw new IllegalArgumentException("未知剧情条件: " + type);
        };
    }

    private static boolean storyBit(CharacterManager manager, int bit) {
        return bit >= 0 && bit < 16
                && (manager.getCharacterData().getStoryLineFlag()[0] & (1 << bit)) != 0;
    }

    private static boolean extraStoryBit(CharacterManager manager, int bit) {
        short[] flags = manager.getCharacterData().getStoryLineFlag();
        return bit >= 0 && bit < 16 && flags != null && flags.length > 1
                && (flags[1] & (1 << bit)) != 0;
    }

    private static boolean badgeBit(CharacterManager manager, int bit) {
        return bit >= 0 && bit < 16
                && (manager.getCharacterData().getBadgeFlag()[0] & (1 << bit)) != 0;
    }

    static long actorId(CharacterManager manager, StoryProgram program, JsonObject action) {
        String actor = StoryProgram.string(action, "actor");
        if (actor == null || actor.isBlank()) {
            return manager.getPalletStory().interactionActorId;
        }
        JsonObject definition = StoryProgram.object(program.actors(), actor);
        int index = StoryProgram.integer(definition, "entityIdx", -1);
        if (index < 0) {
            throw new IllegalStateException("剧情角色没有 entityIdx: " + actor);
        }
        return PalletStoryNpcs.actor(manager, index).getEntityGameId();
    }

    static int actorIndex(StoryProgram program, JsonObject action) {
        String actor = StoryProgram.string(action, "actor");
        if (actor == null || actor.isBlank()) {
            return -1;
        }
        return StoryProgram.integer(StoryProgram.object(program.actors(), actor), "entityIdx", -1);
    }

    static int coordinate(CharacterManager manager, JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return switch (value.getAsString()) {
                case "PLAYER_X" -> manager.getCharacterData().getPlayerEntity().getX();
                case "PLAYER_Y" -> manager.getCharacterData().getPlayerEntity().getY();
                default -> throw new IllegalArgumentException("未知剧情坐标变量: " + value.getAsString());
            };
        }
        throw new IllegalArgumentException("剧情动作缺少坐标: " + key);
    }

    static void changeMap(CharacterManager manager, JsonObject action) {
        String mapKey = StoryProgram.string(action, "map");
        MapData destination = findMap(manager, mapKey);
        if (destination == null) {
            throw new IllegalStateException("剧情目标地图未加载: " + mapKey);
        }
        int x = StoryProgram.integer(action, "x", -1);
        int y = StoryProgram.integer(action, "y", -1);
        int z = StoryProgram.integer(action, "z", 0);
        int toward = StoryProgram.integer(action, "toward", 1);
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("剧情传送坐标无效: " + mapKey);
        }
        PalletStoryState state = manager.getPalletStory();
        state.cancelWait();
        state.awaitingMap = true;
        state.actors = Map.of();
        var player = manager.getCharacterData().getPlayerEntity();
        player.setRegionIndexId(destination.getRegionIndexId());
        player.setMapHeaderIdOrGbaMapGroupId(destination.getMapHeaderIdOrGBAmapGroupId());
        player.setGbaMapId(destination.getGbaMapId());
        player.setX((short) x);
        player.setY((short) y);
        player.setZ((byte) z);
        player.setToward((byte) toward);
        Session session = manager.getCharacterSession();
        long generation = state.generation;
        manager.handleReLoadMap(destination).thenAccept(loaded -> {
            if (!loaded) {
                PalletOpeningService.guard(manager, session, () -> {
                    if (generation == manager.getPalletStory().generation) {
                        throw new IllegalStateException("剧情地图加载超时: " + mapKey);
                    }
                });
            }
        });
    }

    private static MapData findMap(CharacterManager manager, String mapKey) {
        if (mapKey == null) {
            return null;
        }
        for (var region : manager.getScriptManager().getRegionDatas()) {
            if (region == null) continue;
            for (MapData map : region.getRegionMaps().values()) {
                if (mapKey.equals(map.getMapKey())) {
                    return map;
                }
            }
        }
        return null;
    }

    private static void validateOpeningCheckpoint(CharacterManager manager) {
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null || (!PalletStoryNpcs.TOWN.equals(map.getMapKey())
                && !PalletStoryNpcs.LAB.equals(map.getMapKey()))) {
            return;
        }
        PalletStoryProgress progress = PalletOpeningService.progress(manager);
        if (progress.stage() < PalletStoryProgress.RIVAL) {
            progress.validateOpeningCheckpoint();
        }
    }

    private static boolean runFirst(CharacterManager manager, String event, NpcEntity npc,
                                    int x, int y, boolean preemptShop) {
        StoryCatalog catalog = catalog(manager);
        if (catalog == null) {
            return false;
        }
        for (StoryProgram program : catalog.programs().values()) {
            if (!program.enabled()) continue;
            for (JsonObject trigger : program.triggers()) {
                try {
                    if (preemptShop != StoryProgram.bool(trigger, "preemptShop", false)
                            || !matchesTrigger(manager, event, trigger, npc, x, y)) {
                        continue;
                    }
                    if (!idleOrStory(manager)) {
                        return false;
                    }
                    boolean[] handled = {false};
                    PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
                        handled[0] = true;
                        activate(manager, program, trigger, npc);
                        log.info("通用剧情触发: 角色={}, storyId={}, event={}, node={}",
                                PalletOpeningService.characterId(manager), program.id(), event,
                                StoryProgram.string(trigger, "startNode"));
                        StoryActionExecutor.run(manager, program, StoryProgram.string(trigger, "startNode"));
                    });
                    return handled[0];
                } catch (RuntimeException exception) {
                    PalletOpeningService.report(manager, exception);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesTrigger(CharacterManager manager, String event, JsonObject trigger,
                                          NpcEntity npc, int x, int y) {
        if (!event.equals(StoryProgram.string(trigger, "type"))) {
            return false;
        }
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null) return false;
        String mapName = StoryProgram.string(trigger, "map");
        String mapSuffix = StoryProgram.string(trigger, "mapSuffix");
        if (mapName != null && !mapName.equals(map.getMapKey())) return false;
        if (mapSuffix != null && (map.getMapKey() == null || !map.getMapKey().endsWith(mapSuffix))) return false;
        if ("COORDINATE".equals(event)
                && trigger.has("x")
                && trigger.has("y")
                && (StoryProgram.integer(trigger, "x", Integer.MIN_VALUE) != x
                || StoryProgram.integer(trigger, "y", Integer.MIN_VALUE) != y)) {
            return false;
        }
        if ("COORDINATE".equals(event)) {
            String script = StoryProgram.string(trigger, "script");
            if (script != null && map.getCoordinateEvents().stream().noneMatch(coordinate ->
                    coordinate.getX() == x && coordinate.getY() == y
                            && script.equals(coordinate.getEventName()))) {
                return false;
            }
        }
        if ("NPC".equals(event)) {
            if (npc == null) return false;
            int entityIdx = StoryProgram.integer(trigger, "entityIdx", -1);
            if (entityIdx >= 0 && !("npc_" + entityIdx).equals(npc.getNpcName())) return false;
            String npcName = StoryProgram.string(trigger, "npcName");
            if (npcName != null && !npcName.equals(npc.getNpcName())) return false;
            String scriptSuffix = StoryProgram.string(trigger, "scriptSuffix");
            if (scriptSuffix != null && (npc.getInteractionScriptName() == null
                    || !npc.getInteractionScriptName().endsWith(scriptSuffix))) return false;
        }
        for (JsonElement element : StoryProgram.array(trigger, "conditions")) {
            if (!element.isJsonObject() || !matchesCondition(manager, element.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    private static void activate(CharacterManager manager, StoryProgram program,
                                 JsonObject trigger, NpcEntity npc) {
        PalletStoryState state = manager.getPalletStory();
        state.activeStoryId = program.id();
        state.activeNodeId = StoryProgram.string(trigger, "startNode");
        state.interactionActorId = npc == null ? -1 : npc.getEntityGameId();
        state.interactionNpcIndex = npc == null ? -1 : npcIndex(npc);
        if (manager.getInteractManager().getInteractType() != InteractType.STORY) {
            PalletStoryScene.begin(manager);
        }
    }

    private static int npcIndex(NpcEntity npc) {
        String name = npc.getNpcName();
        if (name == null || !name.startsWith("npc_")) return -1;
        try {
            return Integer.parseInt(name.substring(4));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean idleOrStory(CharacterManager manager) {
        InteractType type = manager.getInteractManager().getInteractType();
        return (type == InteractType.NONE || type == InteractType.STORY)
                && manager.getBattleManager() == null && !TradeManager.isInTrade(manager);
    }

    private static boolean idle(CharacterManager manager) {
        return manager.getInteractManager().getInteractType() == InteractType.NONE
                && manager.getBattleManager() == null && !TradeManager.isInTrade(manager);
    }
}
