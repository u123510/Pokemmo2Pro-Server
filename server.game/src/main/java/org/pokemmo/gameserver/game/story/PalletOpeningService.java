package org.pokemmo.gameserver.game.story;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendChatMessagePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadDexPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPlayMusicPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendShowPokemonNameplateWidgetPacket;
import org.pokemmo.gameserver.services.story.PalletStoryStore;
import org.server.Session;
import org.server.union.chat.ChatMessage;

/** First story workflow. Other cities have no implicit fallback to the deleted legacy graph. */
@Slf4j
public final class PalletOpeningService {
    private PalletOpeningService() { }

    public static boolean enabled(CharacterManager manager) {
        return manager != null && manager.getScriptManager() != null
                && manager.getScriptManager().getPalletStory() != null
                && manager.getScriptManager().getPalletStory().enabled();
    }

    public static void onLogin(CharacterManager manager) {
        PalletStoryLifecycle.onLogin(manager);
    }

    public static void onMapReady(CharacterManager manager) {
        if (!enabled(manager)) {
            log.debug("真新镇剧情地图检查已跳过: 剧情配置未加载或未启用");
            return;
        }
        if (!PalletStoryNpcs.map(manager, PalletStoryNpcs.TOWN)
                && !PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB)) return;
        guard(manager, manager.getCharacterSession(), () -> {
            PalletStoryState state = manager.getPalletStory();
            if (state.timer != null && !state.awaitingMap) return;
            state.awaitingMap = false;
            PalletStoryProgress progress = progress(manager);
            PlayerEntity player = manager.getCharacterData().getPlayerEntity();
            log.debug("真新镇剧情地图检查: 角色编号={}, 地图={}, 坐标=({},{}), 阶段={}, 初始选择={}, 交互={}, 战斗中={}",
                    characterId(manager), manager.getCurrentMapDatas()[0].getMapKey(), player.getX(), player.getY(),
                    progress.stage(), progress.starter(), manager.getInteractManager().getInteractType(), manager.getBattleManager() != null);
            PalletStoryNpcs.refresh(manager);
            if (manager.getBattleManager() != null || progress.completed()) return;
            if (!idleOrStory(manager)) return;
            progress.validateOpeningCheckpoint();
            if (shouldStartOnMapReady(manager, progress)) {
                onStep(manager, player.getX(), player.getY());
                return;
            }
            if (progress.stage() == PalletStoryProgress.ESCORT && idleOrStory(manager)) {
                PalletStoryScene.begin(manager);
                if (PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB)) introduceStarters(manager);
                else if (PalletStoryNpcs.map(manager, PalletStoryNpcs.TOWN)) escort(manager);
                else PalletStoryScene.end(manager);
            }
        });
    }

    public static void onDisconnect(CharacterManager manager, Session disconnected) {
        PalletStoryLifecycle.onDisconnect(manager, disconnected);
    }

    public static boolean onStep(CharacterManager manager, int x, int y) {
        if (!enabled(manager)) return false;
        boolean townTrigger = isTownTrigger(manager, x, y);
        boolean labTrigger = PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB) && x >= 5 && x <= 7 && y == 8;
        if (!townTrigger && !labTrigger) return false;
        PalletStoryProgress progress;
        try { progress = progress(manager); }
        catch (RuntimeException exception) { report(manager, exception); return true; }
        log.debug("真新镇剧情触发检查: 角色编号={}, 坐标=({},{}), 阶段={}, 初始选择={}, 交互={}, 战斗中={}",
                characterId(manager), x, y, progress.stage(), progress.starter(),
                manager.getInteractManager().getInteractType(), manager.getBattleManager() != null);
        if (progress.completed() || (townTrigger && progress.stage() >= PalletStoryProgress.CHOOSE)
                || (labTrigger && progress.stage() < PalletStoryProgress.CHOOSE)) return false;
        guard(manager, manager.getCharacterSession(), () -> {
            if (!idleOrStory(manager)) return;
            progress(manager).validateOpeningCheckpoint();
            PalletStoryScene.begin(manager);
            if (townTrigger) {
                advance(manager, PalletStoryProgress.NEW, PalletStoryProgress.ESCORT);
                if (progress(manager).stage() != PalletStoryProgress.ESCORT) { PalletStoryScene.end(manager); return; }
                PalletStoryNpcs.refresh(manager);
                manager.getCharacterSession().send(new SendPlayMusicPacket((byte) 0, (short) 302, false));
                PalletStoryScene.say(manager, -1, "stop", () -> approach(manager));
            } else if (progress(manager).stage() == PalletStoryProgress.CHOOSE) {
                PalletStoryScene.say(manager, actor(manager, 3), "leaveBeforeChoice",
                        () -> PalletStoryScene.walkPlayer(manager, x, 7, () -> PalletStoryScene.end(manager)));
            } else {
                challenge(manager);
            }
        });
        return true;
    }

    public static boolean onNpc(CharacterManager manager, NpcEntity npc) {
        if (!enabled(manager)) return false;
        boolean house = PalletStoryNpcs.map(manager, PalletStoryNpcs.HOUSE) && npc.getNpcName().equals("npc_0");
        boolean oak = PalletStoryNpcs.map(manager, PalletStoryNpcs.TOWN) && npc.getNpcName().equals("npc_2");
        boolean lab = PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB)
                && npc.getNpcName().matches("npc_[0-9]");
        if (!house && !oak && !lab) return false;
        guard(manager, manager.getCharacterSession(), () -> {
            if (!idleOrStory(manager)) return;
            if (!house) progress(manager).validateOpeningCheckpoint();
            PalletStoryScene.begin(manager);
            if (house) { PalletStoryParty.mother(manager, npc); return; }
            if (oak) { escort(manager); return; }
            PalletStoryProgress progress = progress(manager);
            int index = Integer.parseInt(npc.getNpcName().substring(4));
            if (index >= 4 && index <= 6 && progress.stage() == PalletStoryProgress.CHOOSE) {
                selectStarter(manager, npc, index == 4 ? 0 : index == 6 ? 1 : 2);
            } else if ((index == 3 || index == 7) && progress.stage() == PalletStoryProgress.RIVAL) {
                challenge(manager);
            } else {
                String text = index == 3 ? "chooseAgain" : index >= 8 ? "dex" : index >= 4 && index <= 6 ? "ball" : "aide";
                if (progress.completed() && (index == 3 || index == 7)) {
                    notify(manager, "真新镇开场已完成，可以继续旅行。");
                    PalletStoryScene.end(manager);
                } else PalletStoryScene.say(manager, npc.getEntityGameId(), text, () -> PalletStoryScene.end(manager));
            }
        });
        return true;
    }

    public static void onReply(CharacterManager manager, Session session, byte sequence, byte choice) {
        guard(manager, session, () -> {
            if (manager.getInteractManager().getInteractType() == InteractType.STORY) {
                PalletStoryScene.reply(manager, sequence, Byte.toUnsignedInt(choice));
            }
        });
    }

    private static void approach(CharacterManager manager) {
        int x = manager.getCharacterData().getPlayerEntity().getX();
        PalletStoryScene.walkNpc(manager, 2, x, 2, () ->
                PalletStoryScene.say(manager, actor(manager, 2), "follow", () -> escort(manager)));
    }

    private static void escort(CharacterManager manager) {
        if (progress(manager).stage() != PalletStoryProgress.ESCORT) { PalletStoryScene.end(manager); return; }
        if (!PalletStoryNpcs.map(manager, PalletStoryNpcs.TOWN)) { warpToLab(manager); return; }
        PalletStoryScene.closeDialog(manager);
        PalletStoryScene.guide(manager, 2, 16, 14, 16, 15, () -> warpToLab(manager));
    }

    private static void warpToLab(CharacterManager manager) {
        MapData lab = manager.getScriptManager().getRegionDatas()[0].getMapData((byte) 4, (byte) 3);
        if (lab == null) throw new IllegalStateException("研究所地图未加载");
        manager.getPalletStory().cancelWait();
        manager.getPalletStory().actors = Map.of();
        manager.getPalletStory().awaitingMap = true;
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        player.setRegionIndexId((byte) 0);
        player.setMapHeaderIdOrGbaMapGroupId((byte) 4);
        player.setGbaMapId((byte) 3);
        player.setX((short) 6);
        player.setY((short) 11);
        player.setZ((byte) 2);
        player.setToward((byte) 1);
        Session session = manager.getCharacterSession();
        long generation = manager.getPalletStory().generation;
        manager.handleReLoadMap(lab).thenAccept(loaded -> {
            if (!loaded) guard(manager, session, () -> {
                if (generation == manager.getPalletStory().generation) throw new IllegalStateException("剧情地图加载超时");
            });
        });
        // RequestPlayerPacket calls onMapReady after the actual 0x05 confirmation, not a fixed delay.
    }

    private static void introduceStarters(CharacterManager manager) {
        PalletStoryScene.walkPlayer(manager, 6, 4, () ->
                PalletStoryScene.say(manager, actor(manager, 7), "rivalWaiting", () ->
                PalletStoryScene.say(manager, actor(manager, 3), "choose", () ->
                PalletStoryScene.say(manager, actor(manager, 7), "rivalWants", () ->
                PalletStoryScene.say(manager, actor(manager, 3), "oakWait", () -> {
                    advance(manager, PalletStoryProgress.ESCORT, PalletStoryProgress.CHOOSE);
                    PalletStoryScene.end(manager);
                })))));
    }

    private static void selectStarter(CharacterManager manager, NpcEntity npc, int choice) {
        PalletStoryCatalog.Starter starter = manager.getScriptManager().getPalletStory().starter(choice);
        manager.getCharacterSession().send(new SendShowPokemonNameplateWidgetPacket((byte) 0, (short) starter.species(), false));
        PalletStoryScene.prompt(manager, npc.getEntityGameId(), starter.prompt(), true, answer -> {
            if (answer == 0) { PalletStoryScene.end(manager); return; }
            if (!claimStarter(manager, choice)) {
                PalletStoryScene.end(manager);
                return;
            }
            PalletStoryScene.say(manager, actor(manager, 3), "obtained", () ->
                    PalletStoryScene.say(manager, actor(manager, 7), "rivalChooses", () ->
                    PalletStoryScene.say(manager, actor(manager, 7), "rivalObtained", () -> PalletStoryScene.end(manager))));
        });
    }

    static boolean claimStarter(CharacterManager manager, int choice) {
        PalletStoryCatalog.Starter starter = manager.getScriptManager().getPalletStory().starter(choice);
        PokemonData candidate = PalletStarterFactory.create(manager, starter, true);
        PalletStoryStore.Result result = manager.getCharacterService().getPalletStoryStore().claimStarter(
                manager.getCharacterData().getAccountId(), characterId(manager), (short) choice, candidate);
        apply(manager, result.progress());
        if (result.starter() == null) {
            PalletStoryParty.refresh(manager);
            return false;
        }
        manager.getPartyPokemons()[result.starter().getContainerPosition()] = result.starter();
        manager.getCharacterSession().send(new SendAddPokemonPacket(result.starter()),
                new SendLoadDexPacket(manager.getCharacterService().getPokemonDexUnlockDataById(characterId(manager))),
                new SendShowPokemonNameplateWidgetPacket((byte) 0, (short) -1, false));
        PalletStoryNpcs.refresh(manager);
        return true;
    }

    private static void challenge(CharacterManager manager) {
        PalletStoryScene.say(manager, actor(manager, 7), "challenge", () -> PalletStoryBattle.start(manager));
    }

    static PalletStoryProgress progress(CharacterManager manager) {
        if (manager.getPalletStory().progressLoadFailed) {
            throw new IllegalStateException("剧情存档尚未成功读取，请检查服务器日志并重新登录");
        }
        if (manager.getPalletStory().progress == null) manager.getPalletStory().progress = PalletStoryProgress.from(manager.getCharacterData());
        return manager.getPalletStory().progress;
    }

    static boolean shouldStartOnMapReady(CharacterManager manager, PalletStoryProgress progress) {
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        return progress.stage() == PalletStoryProgress.NEW && progress.starter() == 3
                && isTownTrigger(manager, player.getX(), player.getY()) && idleOrStory(manager);
    }

    private static boolean isTownTrigger(CharacterManager manager, int x, int y) {
        return PalletStoryNpcs.map(manager, PalletStoryNpcs.TOWN) && (x == 12 || x == 13) && y == 1;
    }

    static void apply(CharacterManager manager, PalletStoryProgress progress) {
        progress.apply(manager.getCharacterData());
        manager.getPalletStory().progress = progress;
        log.info("真新镇剧情检查点已保存: 角色编号={}, 阶段={}, 初始选择={}", characterId(manager), progress.stage(), progress.starter());
    }

    private static void advance(CharacterManager manager, short expected, short next) {
        apply(manager, manager.getCharacterService().getPalletStoryStore().advance(
                manager.getCharacterData().getAccountId(), characterId(manager), expected, next));
    }

    static long characterId(CharacterManager manager) { return manager.getCharacterData().getPlayerEntity().getEntityGameId(); }
    private static long actor(CharacterManager manager, int index) { return PalletStoryNpcs.actor(manager, index).getEntityGameId(); }

    static boolean idleOrStory(CharacterManager manager) {
        InteractType type = manager.getInteractManager().getInteractType();
        return (type == InteractType.NONE || type == InteractType.STORY)
                && manager.getBattleManager() == null && !TradeManager.isInTrade(manager);
    }

    static void guard(CharacterManager manager, Session session, Runnable action) {
        synchronized (manager.getInteractManager()) {
            if (session == null || !session.isActive() || manager.getCharacterSession() != session
                    || session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() != manager) return;
            try { action.run(); }
            catch (RuntimeException exception) { report(manager, exception); }
        }
    }

    static void report(CharacterManager manager, RuntimeException exception) {
        log.error("剧情执行失败: 角色编号={}", characterId(manager), exception);
        PalletStoryScene.end(manager);
        notify(manager, "剧情暂时无法继续：" + exception.getMessage() + "。已保存的进度不会重置。");
    }

    public static void notify(CharacterManager manager, String message) {
        manager.getCharacterSession().send(new SendChatMessagePacket(ChatMessage.gameNotification(message)));
    }
}
