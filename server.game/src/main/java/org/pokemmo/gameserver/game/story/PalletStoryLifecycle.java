package org.pokemmo.gameserver.game.story;

import java.util.Map;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.server.Session;

/** Reloads durable progress without replacing online assets or an active battle. */
@Slf4j
final class PalletStoryLifecycle {
    private PalletStoryLifecycle() { }

    static void onLogin(CharacterManager manager) {
        if (!StoryService.enabled(manager)) return;
        reload(manager, () -> manager.getCharacterService().getPalletStoryStore().loadProgress(
                manager.getCharacterData().getAccountId(), PalletOpeningService.characterId(manager)));
    }

    static void reload(CharacterManager manager, Supplier<PalletStoryProgress> readProgress) {
        synchronized (manager.getInteractManager()) {
            String battleStoryId = manager.getPalletStory().storyBattleId;
            String battleFinishNode = manager.getPalletStory().storyBattleFinishNode;
            String battleResult = manager.getPalletStory().storyBattleResult;
            String battleProgramId = manager.getPalletStory().activeStoryId;
            boolean hasBattle = manager.getBattleManager() != null;
            resetScene(manager);
            PalletStoryState state = manager.getPalletStory();
            if (hasBattle) {
                state.storyBattleId = battleStoryId;
                state.storyBattleFinishNode = battleFinishNode;
                state.storyBattleResult = battleResult;
                state.activeStoryId = battleProgramId;
            }
            state.progressLoadFailed = false;
            try {
                PalletStoryProgress latest = hasBattle
                        ? PalletStoryProgress.from(manager.getCharacterData()) : readProgress.get();
                if (latest == null) throw new IllegalStateException("剧情存档查询未返回结果");
                latest.apply(manager.getCharacterData());
                state.progress = latest;
                if (manager.getCharacterService() != null) {
                    var flags = manager.getCharacterService().getStoryEventFlagStore();
                    long characterId = PalletOpeningService.characterId(manager);
                    state.eventFlags = flags.load(characterId);
                    state.eliteFourStage = flags.loadEliteFourStage(characterId);
                } else {
                    throw new IllegalStateException("角色数据库服务尚未初始化");
                }
                var player = manager.getCharacterData().getPlayerEntity();
                log.info("真新镇剧情进度已加载: 角色编号={}, 账号编号={}, 来源={}, 阶段={}, 初始选择={}, 地图=({},{},{}), 坐标=({},{})",
                        PalletOpeningService.characterId(manager), manager.getCharacterData().getAccountId(),
                        hasBattle ? "保留当前战斗进度" : "数据库", latest.stage(), latest.starter(),
                        Byte.toUnsignedInt(player.getRegionIndexId()), Byte.toUnsignedInt(player.getMapHeaderIdOrGbaMapGroupId()),
                        Byte.toUnsignedInt(player.getGbaMapId()), player.getX(), player.getY());
                PalletStoryBattle.resumeAfterLogin(manager);
            } catch (RuntimeException exception) {
                state.progress = null;
                state.progressLoadFailed = true;
                log.error("真新镇剧情存档读取失败，暂停剧情触发并保留角色资产: 角色编号={}",
                        PalletOpeningService.characterId(manager), exception);
            }
        }
    }

    static void onDisconnect(CharacterManager manager, Session disconnected) {
        if (manager.getCharacterSession() != disconnected) return;
        synchronized (manager.getInteractManager()) {
            if (manager.getCharacterSession() == disconnected) {
                String battleStoryId = manager.getPalletStory().storyBattleId;
                String battleFinishNode = manager.getPalletStory().storyBattleFinishNode;
                String battleResult = manager.getPalletStory().storyBattleResult;
                String battleProgramId = manager.getPalletStory().activeStoryId;
                boolean hasBattle = manager.getBattleManager() != null;
                resetScene(manager);
                if (hasBattle) {
                    manager.getPalletStory().storyBattleId = battleStoryId;
                    manager.getPalletStory().storyBattleFinishNode = battleFinishNode;
                    manager.getPalletStory().storyBattleResult = battleResult;
                    manager.getPalletStory().activeStoryId = battleProgramId;
                }
            }
        }
    }

    private static void resetScene(CharacterManager manager) {
        PalletStoryState state = manager.getPalletStory();
        state.cancelWait();
        state.clearStoryContext();
        state.nurseInteraction = false;
        state.actors = Map.of();
        state.awaitingMap = false;
        if (manager.getInteractManager().getInteractType() == InteractType.STORY) {
            manager.getInteractManager().setCurrentInteractScript(null);
            manager.getInteractManager().setInteractType(InteractType.NONE);
            manager.getInteractManager().clearLastInteractorEntityId();
        }
    }
}
