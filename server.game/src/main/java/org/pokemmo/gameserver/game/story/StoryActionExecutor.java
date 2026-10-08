package org.pokemmo.gameserver.game.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.events.EventRegionType;
import org.pokemmo.gameserver.game.interact.TrainerInteractionService;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.trainer.TrainerTeamData;
import org.pokemmo.gameserver.protocol.packets.s2c.SendGameSideEventFlagPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadDexPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPlayMusicPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShowPokemonNameplateWidgetPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSetEntityPosPacket;
import org.pokemmo.gameserver.services.story.OakParcelStore;
import org.pokemmo.gameserver.services.story.OakParcelStore.Action;

/** Executes the finite action vocabulary exposed by generic story JSONC. */
final class StoryActionExecutor {
    private StoryActionExecutor() {
    }

    static void run(CharacterManager manager, StoryProgram program, String nodeId) {
        if (program == null || nodeId == null) {
            throw new IllegalArgumentException("剧情节点不能为空");
        }
        JsonObject node = program.node(nodeId);
        if (node == null) {
            throw new IllegalStateException("剧情节点不存在: " + program.id() + "#" + nodeId);
        }
        manager.getPalletStory().activeStoryId = program.id();
        manager.getPalletStory().activeNodeId = nodeId;
        String actionKey = program.id() + "#" + nodeId;
        var plan = StoryActionTransaction.prepare(manager, program, node, actionKey);
        if (plan != null) {
            var result = manager.getCharacterService().getStoryActionStore().commit(
                    manager.getCharacterData().getAccountId(),
                    PalletOpeningService.characterId(manager), plan);
            StoryActionTransaction.apply(manager, plan, result);
            manager.getPalletStory().durableActionKey = actionKey;
        } else {
            manager.getPalletStory().durableActionKey = null;
        }
        executeActions(manager, program, node, 0);
    }

    private static void executeActions(CharacterManager manager, StoryProgram program,
                                       JsonObject node, int actionIndex) {
        JsonArray actions = StoryProgram.array(node, "actions");
        if (actionIndex >= actions.size()) {
            String next = StoryProgram.string(node, "next");
            if (next == null) {
                StoryActionTransaction.finish(manager);
                return;
            }
            run(manager, program, next);
            return;
        }
        JsonElement element = actions.get(actionIndex);
        if (!element.isJsonObject()) {
            throw new IllegalStateException("剧情动作必须是对象: " + program.id());
        }
        JsonObject action = element.getAsJsonObject();
        String type = StoryProgram.string(action, "type");
        if (type == null) {
            throw new IllegalStateException("剧情动作缺少 type: " + program.id());
        }
        manager.getPalletStory().activeNodeId = nodeId(node, program);
        switch (type) {
            case "DIALOGUE" -> dialogue(manager, program, action,
                    () -> executeActions(manager, program, node, actionIndex + 1));
            case "YES_NO" -> yesNo(manager, program, action);
            case "BRANCH" -> branch(manager, program, action);
            case "GOTO" -> run(manager, program, StoryProgram.string(action, "node"));
            case "SET_OPENING_STAGE" -> {
                int expected = StoryProgram.integer(action, "expected", -1);
                int next = StoryProgram.integer(action, "nextStage", -1);
                PalletStoryProgress progress = manager.getCharacterService().getPalletStoryStore().advance(
                        manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager),
                        (short) expected, (short) next);
                PalletOpeningService.apply(manager, progress);
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "PLAY_MUSIC" -> {
                int musicId = StoryProgram.integer(action, "musicId", -1);
                if (musicId < 0 || musicId > Short.MAX_VALUE) {
                    throw new IllegalArgumentException("剧情音乐编号无效: " + musicId);
                }
                manager.getCharacterSession().send(new SendPlayMusicPacket((byte) 0, (short) musicId, false));
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "MOVE_PLAYER" -> {
                int x = StoryRuntime.coordinate(manager, action, "x");
                int y = StoryRuntime.coordinate(manager, action, "y");
                PalletStoryScene.walkPlayer(manager, x, y,
                        () -> executeActions(manager, program, node, actionIndex + 1));
            }
            case "MOVE_NPC" -> {
                int actor = StoryRuntime.actorIndex(program, action);
                JsonObject target = StoryProgram.object(action, "target");
                int x = StoryRuntime.coordinate(manager, target, "x");
                int y = StoryRuntime.coordinate(manager, target, "y");
                if (actor < 0) throw new IllegalArgumentException("剧情 MOVE_NPC 缺少 actor");
                PalletStoryScene.walkNpc(manager, actor, x, y,
                        () -> executeActions(manager, program, node, actionIndex + 1));
            }
            case "GUIDE" -> {
                int actor = StoryRuntime.actorIndex(program, action);
                int npcX = StoryProgram.integer(action, "npcX", -1);
                int npcY = StoryProgram.integer(action, "npcY", -1);
                int playerX = StoryProgram.integer(action, "playerX", -1);
                int playerY = StoryProgram.integer(action, "playerY", -1);
                PalletStoryScene.guide(manager, actor, npcX, npcY, playerX, playerY,
                        () -> executeActions(manager, program, node, actionIndex + 1));
            }
            case "CHANGE_MAP" -> {
                StoryActionTransaction.finish(manager);
                StoryRuntime.changeMap(manager, action);
            }
            case "CLOSE_DIALOG" -> {
                PalletStoryScene.closeDialog(manager);
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "SELECT_STARTER" -> selectStarter(manager, program, action);
            case "START_BATTLE" -> startBattle(manager, program, action);
            case "START_TRAINER_BATTLE" -> startTrainerBattle(manager, program, action);
            case "SET_STORY_BIT" -> {
                if (!StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    setStoryBit(manager, action);
                }
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "SET_EXTRA_STORY_BIT" -> {
                if (!StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    setExtraStoryBit(manager, action);
                }
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "SET_BADGE" -> {
                if (!StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    setBadge(manager, action);
                }
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "SET_CHAMPION" -> {
                if (!StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    setChampion(manager);
                }
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "SET_ELITE_STAGE" -> {
                int stage = StoryProgram.integer(action, "stage", -1);
                if (stage < 0 || stage > 4) {
                    throw new IllegalArgumentException("四天王阶段无效: " + stage);
                }
                long characterId = PalletOpeningService.characterId(manager);
                if (!manager.getCharacterService().getStoryEventFlagStore()
                        .setEliteFourStage(characterId, stage)) {
                    throw new IllegalStateException("四天王阶段保存失败: " + stage);
                }
                manager.getPalletStory().setEliteFourStage(stage);
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "GRANT_ITEM" -> {
                if (StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    executeActions(manager, program, node, actionIndex + 1);
                } else {
                    grantItem(manager, action,
                            () -> executeActions(manager, program, node, actionIndex + 1));
                }
            }
            case "REMOVE_ITEM" -> {
                if (!StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    removeItem(manager, action);
                }
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "GRANT_POKEMON" -> {
                if (!StoryActionTransaction.skip(manager, program.id() + "#" + nodeId(node, program), type)) {
                    grantPokemon(manager, action);
                }
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "START_SAFARI" -> {
                if (startSafari(manager, action)) {
                    executeActions(manager, program, node, actionIndex + 1);
                } else {
                    PalletStoryScene.end(manager);
                }
            }
            case "RUN_MAP_READY" -> {
                StoryActionTransaction.finish(manager);
                PalletStoryScene.end(manager);
                StoryRuntime.onMapReady(manager);
            }
            case "REJECT_MOVE" -> rejectMove(manager);
            case "PARCEL_OPERATION" -> parcelOperation(manager, action,
                    () -> executeActions(manager, program, node, actionIndex + 1));
            case "HEAL_PARTY" -> {
                long characterId = PalletOpeningService.characterId(manager);
                var result = manager.getCharacterService().healPartyFully(characterId, manager.getPartyPokemons());
                if (!result.success()) throw new IllegalStateException(result.reason());
                PalletStoryParty.refresh(manager);
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "NOTIFY" -> {
                PalletOpeningService.notify(manager, StoryProgram.string(action, "message"));
                executeActions(manager, program, node, actionIndex + 1);
            }
            case "CLOSE_SCENE" -> {
                StoryActionTransaction.finish(manager);
                PalletStoryScene.end(manager);
            }
            default -> throw new IllegalArgumentException("未知剧情动作: " + type);
        }
    }

    private static void dialogue(CharacterManager manager, StoryProgram program, JsonObject action,
                                 Runnable next) {
        int text = program.text(StoryProgram.string(action, "text"));
        if (text <= 0) throw new IllegalStateException("剧情文本引用无效");
        PalletStoryScene.prompt(manager, StoryRuntime.actorId(manager, program, action), text,
                false, ignored -> next.run());
    }

    private static void yesNo(CharacterManager manager, StoryProgram program, JsonObject action) {
        int text = program.text(StoryProgram.string(action, "text"));
        String yes = StoryProgram.string(action, "yes");
        String no = StoryProgram.string(action, "no");
        PalletStoryScene.prompt(manager, StoryRuntime.actorId(manager, program, action), text, true,
                answer -> run(manager, program, answer == 1 ? yes : no));
    }

    private static void branch(CharacterManager manager, StoryProgram program, JsonObject action) {
        JsonObject condition = StoryProgram.object(action, "condition");
        String target = StoryRuntime.matchesCondition(manager, condition)
                ? StoryProgram.string(action, "yes") : StoryProgram.string(action, "no");
        run(manager, program, target);
    }

    private static void selectStarter(CharacterManager manager, StoryProgram program, JsonObject action) {
        int npcIndex = manager.getPalletStory().interactionNpcIndex;
        int choice = switch (npcIndex) {
            case 4 -> 0;
            case 5 -> 2;
            case 6 -> 1;
            default -> throw new IllegalStateException("无法从剧情 NPC 推断初始选择: " + npcIndex);
        };
        JsonObject starter = starter(program, choice);
        int species = StoryProgram.integer(starter, "species", -1);
        int prompt = StoryProgram.integer(starter, "prompt", -1);
        long actor = manager.getPalletStory().interactionActorId;
        manager.getCharacterSession().send(new SendShowPokemonNameplateWidgetPacket(
                (byte) 0, (short) species, false));
        PalletStoryScene.prompt(manager, actor, prompt, true, answer -> {
            if (answer == 0) {
                PalletStoryScene.end(manager);
                return;
            }
            if (!PalletOpeningService.claimStarter(manager, choice)) {
                PalletStoryScene.end(manager);
                return;
            }
            run(manager, program, StoryProgram.string(action, "next"));
        });
    }

    private static JsonObject starter(StoryProgram program, int choice) {
        for (JsonElement element : StoryProgram.array(program.resources(), "starters")) {
            if (element.isJsonObject() && StoryProgram.integer(element.getAsJsonObject(), "choice", -1) == choice) {
                return element.getAsJsonObject();
            }
        }
        throw new IllegalStateException("缺少初始宝可梦配置: " + choice);
    }

    private static void startBattle(CharacterManager manager, StoryProgram program, JsonObject action) {
        String battleType = StoryProgram.string(action, "battleType");
        switch (battleType == null ? "" : battleType) {
            case "OPENING_RIVAL" -> {
                PalletStoryBattle.start(manager);
                manager.getPalletStory().activeStoryId = program.id();
                manager.getPalletStory().storyBattleId = battleType;
                manager.getPalletStory().storyBattleFinishNode =
                        StoryProgram.string(action, "finishNode");
            }
            case "CAPTURE_TUTORIAL" -> {
                ViridianCatchStory.startBattle(manager);
                manager.getPalletStory().activeStoryId = program.id();
                manager.getPalletStory().storyBattleId = battleType;
                manager.getPalletStory().storyBattleFinishNode =
                        StoryProgram.string(action, "finishNode");
            }
            default -> throw new IllegalArgumentException("未知剧情战斗类型: " + battleType);
        }
    }

    private static void startTrainerBattle(CharacterManager manager, StoryProgram program, JsonObject action) {
        int trainerTeamId = StoryProgram.integer(action, "trainerTeamId", -1);
        JsonArray trainerTeamIds = StoryProgram.array(action, "trainerTeamIds");
        if (trainerTeamIds.size() >= 3) {
            int starter = PalletOpeningService.progress(manager).starter();
            if (starter >= 0 && starter < 3) {
                trainerTeamId = trainerTeamIds.get(starter).getAsInt();
            }
        }
        if (trainerTeamId >= 0) {
            TrainerTeamData definition = manager.getScriptManager().getTrainerTeamManager()
                    .getTrainerTeam((short) trainerTeamId);
            PalletStoryScene.closeDialog(manager);
            if (!TrainerInteractionService.startDefinition(manager, definition, () -> {
                manager.getPalletStory().activeStoryId = program.id();
                manager.getPalletStory().storyBattleId = StoryProgram.string(action, "battleId");
                manager.getPalletStory().storyBattleResult = null;
                manager.getPalletStory().storyBattleFinishNode =
                        StoryProgram.string(action, "finishNode");
            })) {
                throw new IllegalStateException("原版训练家队伍启动失败: " + trainerTeamId);
            }
            return;
        }
        int trainerModel = StoryProgram.integer(action, "trainerModel", 328);
        int money = StoryProgram.integer(action, "money", 0);
        String battleId = StoryProgram.string(action, "battleId");
        String battleType = StoryProgram.string(action, "battleType");
        PokemonData[] team = StoryTrainerFactory.createTeam(
                manager.getCharacterSession(), StoryProgram.array(action, "team"));
        StoryBattleStarter.start(manager, battleId, battleType, trainerModel, money, team);
        manager.getPalletStory().activeStoryId = program.id();
        manager.getPalletStory().storyBattleFinishNode =
                StoryProgram.string(action, "finishNode");
    }

    private static void setStoryBit(CharacterManager manager, JsonObject action) {
        int bit = StoryProgram.integer(action, "bit", -1);
        var result = manager.getCharacterService().getStoryProgressStore().setKantoStoryBit(
                manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager), bit);
        manager.getCharacterData().getStoryLineFlag()[0] = result.value();
        PalletStoryNpcs.refresh(manager);
    }

    private static void setExtraStoryBit(CharacterManager manager, JsonObject action) {
        int bit = StoryProgram.integer(action, "bit", -1);
        var result = manager.getCharacterService().getStoryProgressStore().setKantoExtraStoryBit(
                manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager), bit);
        manager.getCharacterData().getStoryLineFlag()[1] = result.value();
        PalletStoryNpcs.refresh(manager);
    }

    private static void setBadge(CharacterManager manager, JsonObject action) {
        int bit = StoryProgram.integer(action, "bit", -1);
        var result = manager.getCharacterService().getStoryProgressStore().setKantoBadgeBit(
                manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager), bit);
        manager.getCharacterData().getBadgeFlag()[0] = result.value();
        PalletStoryNpcs.refresh(manager);
        manager.getCharacterSession().send(new SendGameSideEventFlagPacket(
                EventRegionType.KANTO.getType(),
                manager.getActiveGameEvents(EventRegionType.KANTO)));
    }

    private static void setChampion(CharacterManager manager) {
        manager.getCharacterService().getStoryProgressStore().setKantoChampion(
                manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager));
        manager.getCharacterData().getChampionFlag()[0] = true;
        PalletStoryNpcs.refresh(manager);
        manager.getCharacterSession().send(new SendGameSideEventFlagPacket(
                EventRegionType.KANTO.getType(),
                manager.getActiveGameEvents(EventRegionType.KANTO)));
    }

    private static void grantItem(CharacterManager manager, JsonObject action, Runnable next) {
        int itemId = StoryProgram.integer(action, "itemId", 0);
        int amount = StoryProgram.integer(action, "amount", 0);
        if (itemId <= 0 || amount <= 0 || amount > Short.MAX_VALUE) {
            throw new IllegalArgumentException("剧情奖励道具参数无效");
        }
        if (manager.getCharacterService().addInventoryItem(
                PalletOpeningService.characterId(manager), (short) itemId, (short) amount,
                manager.getSnowflakeIdGenerator().nextId()) == null) {
            throw new IllegalStateException("剧情奖励道具发放失败: " + itemId);
        }
        var inventory = manager.getCharacterService().getInventory();
        manager.getCharacterSession().send(new SendInventoryPacket(inventory,
                manager.getCharacterService().getItemsByContainerAndCharacter(
                        PalletOpeningService.characterId(manager), inventory)));
        next.run();
    }

    private static void removeItem(CharacterManager manager, JsonObject action) {
        int itemId = StoryProgram.integer(action, "itemId", 0);
        int amount = StoryProgram.integer(action, "amount", 0);
        if (itemId <= 0 || amount <= 0 || amount > Short.MAX_VALUE) {
            throw new IllegalArgumentException("剧情扣除道具参数无效");
        }
        long characterId = PalletOpeningService.characterId(manager);
        var owned = manager.getCharacterService().getOwnedItemByIndex(characterId, (short) itemId);
        if (owned == null || !manager.getCharacterService().removeStoryItem(
                characterId, owned.getItemId(), (short) amount)) {
            throw new IllegalStateException("剧情扣除道具失败: " + itemId);
        }
        var inventory = manager.getCharacterService().getInventory();
        manager.getCharacterSession().send(new SendInventoryPacket(inventory,
                manager.getCharacterService().getItemsByContainerAndCharacter(characterId, inventory)));
    }

    private static void grantPokemon(CharacterManager manager, JsonObject action) {
        int species = StoryProgram.integer(action, "species", 0);
        int level = StoryProgram.integer(action, "level", 0);
        if (species <= 0 || level < 1 || level > 100
                || PokemonManager.getPokemonoexData(species) == null) {
            throw new IllegalArgumentException("剧情奖励宝可梦参数无效");
        }
        JsonArray configuredMoves = StoryProgram.array(action, "moves");
        short[] moves = new short[Math.min(4, configuredMoves.size())];
        for (int index = 0; index < moves.length; index++) {
            moves[index] = configuredMoves.get(index).getAsShort();
        }
        StoryPokemonFactory.grant(manager, species, level, moves);
    }

    private static boolean startSafari(CharacterManager manager, JsonObject action) {
        int fee = StoryProgram.integer(action, "fee", 500);
        int steps = StoryProgram.integer(action, "steps", 500);
        int balls = StoryProgram.integer(action, "balls", 30);
        if (fee < 0 || steps <= 0 || steps > Short.MAX_VALUE || balls <= 0 || balls > 255) {
            throw new IllegalArgumentException("野生原野区入场参数无效");
        }
        long characterId = PalletOpeningService.characterId(manager);
        var result = manager.getCharacterService().startSafari(
                characterId, fee, (short) steps, (short) balls);
        if (!result.success()) {
            PalletOpeningService.notify(manager, "你的钱不够，无法支付野生原野区的入场费。");
            return false;
        }
        manager.getCharacterData().setMoney(result.remainingMoney());
        manager.getCharacterData().setSafariSteps(result.steps());
        manager.getCharacterData().setSafariBallAmount(result.balls());
        manager.getCharacterSession().send(new SendUpdatePlayerInfo(
                new org.pokemmo.gameserver.game.character.UpdateCharacterSelector.Builder()
                        .setCharacterData(manager.getCharacterData())
                        .setRefreshMoney(true)
                        .setRefreshSafariInfo(true)
                        .build()));
        return true;
    }

    private static void rejectMove(CharacterManager manager) {
        var player = manager.getCharacterData().getPlayerEntity();
        switch (Byte.toUnsignedInt(player.getToward())) {
            case 0 -> player.setY((short) (player.getY() - 1));
            case 1 -> player.setY((short) (player.getY() + 1));
            case 2 -> player.setX((short) (player.getX() + 1));
            case 3 -> player.setX((short) (player.getX() - 1));
            default -> {
                PalletStoryScene.end(manager);
                return;
            }
        }
        manager.getCharacterSession().send(new SendSetEntityPosPacket(player));
        manager.broadcastPlayerPosition();
        PalletOpeningService.notify(manager, "前方守卫检查了你的徽章。");
        PalletStoryScene.end(manager);
    }

    private static void parcelOperation(CharacterManager manager, JsonObject action, Runnable next) {
        String operation = StoryProgram.string(action, "operation");
        Action parcelAction = switch (operation == null ? "" : operation) {
            case "PICKUP" -> Action.PICKUP;
            case "DELIVER" -> Action.DELIVER;
            case "RECEIVE_POKEDEX" -> Action.RECEIVE_POKEDEX;
            default -> throw new IllegalArgumentException("未知包裹剧情操作: " + operation);
        };
        String map = parcelAction == Action.PICKUP ? OakParcelStory.MART : PalletStoryNpcs.LAB;
        OakParcelStore.Result result = OakParcelStory.commit(manager, map, parcelAction);
        if (parcelAction == Action.RECEIVE_POKEDEX) {
            manager.getCharacterSession().send(new SendLoadDexPacket(
                    manager.getCharacterService().getPokemonDexUnlockDataById(
                            PalletOpeningService.characterId(manager))));
        }
        next.run();
    }

    private static String nodeId(JsonObject node, StoryProgram program) {
        for (String id : program.nodeIds()) {
            if (program.node(id) == node) return id;
        }
        return managerNodeFallback(program);
    }

    private static String managerNodeFallback(StoryProgram program) {
        return program.id();
    }
}
