package org.pokemmo.gameserver.game.story;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.battle.BattleGenerator;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.battle.FactionResultType;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.game.pool.GameSessionPool;
import org.pokemmo.gameserver.services.story.ViridianCatchStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Viridian City's native capture lesson, reusing the normal wild-battle flow. */
@Slf4j
public final class ViridianCatchStory {
    public static final String MAP = "ViridianCity";
    public static final String NPC = "npc_3";
    private static final ScheduledExecutorService AUTO_CAPTURE = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "viridian-catch-tutorial");
        thread.setDaemon(true);
        return thread;
    });

    private ViridianCatchStory() {
    }

    public static boolean enabled(CharacterManager manager) {
        return manager != null && manager.getScriptManager() != null
                && manager.getScriptManager().getViridianCatchStory() != null
                && manager.getScriptManager().getViridianCatchStory().enabled();
    }

    static void onLogin(CharacterManager manager) {
        Boolean previousComplete = manager.getPalletStory().viridianCatchComplete;
        BattleManager previousBattle = manager.getPalletStory().viridianCatchBattle;
        manager.getPalletStory().viridianCatchComplete = null;
        if (!enabled(manager) || manager.getPalletStory().progressLoadFailed) {
            return;
        }
        try {
            BattleManager battle = previousBattle;
            boolean reconnectingBattle = battle != null && manager.getBattleManager() == battle;
            boolean complete = reconnectingBattle
                    ? Boolean.TRUE.equals(previousComplete)
                    : store(manager).loadComplete(accountId(manager), characterId(manager));
            manager.getPalletStory().viridianCatchComplete = complete;
            log.info("常磐市捕获教学进度已加载: 角色编号={}, 完成={}, 来源={}",
                    characterId(manager), complete, reconnectingBattle ? "保留当前战斗" : "数据库");
        } catch (RuntimeException exception) {
            manager.getPalletStory().viridianCatchComplete = null;
            log.error("常磐市捕获教学进度读取失败，暂停本章: 角色编号={}", characterId(manager), exception);
        }
    }

    static boolean onNpc(CharacterManager manager, NpcEntity npc) {
        if (!enabled(manager) || !isTutorialNpc(manager, npc)) {
            return false;
        }
        try {
            if (!eligible(manager) || !idle(manager)) {
                return false;
            }
        } catch (RuntimeException exception) {
            PalletOpeningService.report(manager, exception);
            return true;
        }
        PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
            if (!idle(manager)) {
                return;
            }
            if (Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete)) {
                begin(manager);
                play(manager, "completed", () -> PalletStoryScene.end(manager));
                return;
            }
            if (!store(manager).hasCaptureBall(accountId(manager), characterId(manager))) {
                PalletOpeningService.notify(manager, "你身上没有精灵球，请先准备至少一个精灵球再接受捕获教学。");
                return;
            }
            begin(manager);
            play(manager, "introduction", () -> startBattle(manager));
        });
        return true;
    }

    static void onMapReady(CharacterManager manager) {
        if (!enabled(manager) || !isViridianMap(manager) || !eligible(manager) || !idle(manager)) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete)
                    || !store(manager).hasCaptureBall(accountId(manager), characterId(manager))) {
                return;
            }
            PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
                if (!isViridianMap(manager) || !eligible(manager) || !idle(manager)
                        || Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete)
                        || !store(manager).hasCaptureBall(accountId(manager), characterId(manager))) {
                    return;
                }
                begin(manager);
                play(manager, "introduction", () -> startBattle(manager));
            });
        } catch (RuntimeException exception) {
            PalletOpeningService.report(manager, exception);
        }
    }

    static void settle(CharacterManager manager, BattleManager battle) {
        if (manager == null || battle == null
                || manager.getPalletStory().viridianCatchBattle != battle) {
            return;
        }
        byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
        if (faction < 0 || faction >= battle.debutFactions.size()
                || battle.debutFactions.get(faction).getFactionStatType() != FactionResultType.CATCH_POKEMON
                || Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete)) {
            return;
        }
        try {
            ViridianCatchStore.Result result = store(manager).complete(
                    accountId(manager), characterId(manager));
            manager.getCharacterData().getStoryLineFlag()[0] = result.kantoStoryFlags();
            manager.getPalletStory().viridianCatchComplete = result.complete();
            log.info("常磐市捕获教学已完成: 角色编号={}, 实际变更={}", characterId(manager), result.changed());
        } catch (RuntimeException exception) {
            log.error("常磐市捕获教学完成标记保存失败: 角色编号={}", characterId(manager), exception);
            PalletOpeningService.notify(manager, "捕获教学已结束，但完成进度保存失败，请重连后重试。");
        }
    }

    static boolean onFinishReply(CharacterManager manager) {
        BattleManager battle = manager.getBattleManager();
        if (battle == null || manager.getPalletStory().viridianCatchBattle != battle) {
            return false;
        }
        byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
        if (faction < 0 || faction >= battle.debutFactions.size()) {
            return false;
        }
        FactionResultType result = battle.debutFactions.get(faction).getFactionStatType();
        if (result == FactionResultType.IN_BATTLE) {
            return false;
        }
        boolean caught = result == FactionResultType.CATCH_POKEMON
                && Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete);
        manager.getPalletStory().viridianCatchBattle = null;
        if (!caught) {
            return false;
        }
        manager.setBattleManager(null);
        GameSessionPool.removeBattleManagerInPool(battle);
        try {
            begin(manager);
            play(manager, "completed", () -> PalletStoryScene.end(manager));
        } catch (RuntimeException exception) {
            PalletOpeningService.report(manager, exception);
        }
        return true;
    }

    static boolean finishBattle(CharacterManager manager) {
        BattleManager battle = manager.getBattleManager();
        if (battle == null || manager.getPalletStory().viridianCatchBattle != battle) {
            return false;
        }
        byte faction = battle.getFactionIndexBySession(manager.getCharacterSession());
        if (faction < 0 || faction >= battle.debutFactions.size()) {
            return false;
        }
        FactionResultType result = battle.debutFactions.get(faction).getFactionStatType();
        if (result == FactionResultType.IN_BATTLE) {
            return false;
        }
        boolean caught = result == FactionResultType.CATCH_POKEMON
                && Boolean.TRUE.equals(manager.getPalletStory().viridianCatchComplete);
        manager.getPalletStory().viridianCatchBattle = null;
        if (!caught) {
            return false;
        }
        manager.setBattleManager(null);
        GameSessionPool.removeBattleManagerInPool(battle);
        return true;
    }

    static boolean isTutorialNpc(CharacterManager manager, NpcEntity npc) {
        return npc != null && isViridianMap(manager)
                && NPC.equals(npc.getNpcName());
    }

    private static boolean isViridianMap(CharacterManager manager) {
        return PalletStoryNpcs.map(manager, MAP);
    }

    static boolean eligible(CharacterManager manager) {
        if (manager.getPalletStory().viridianCatchComplete == null) {
            return false;
        }
        return PalletOpeningService.progress(manager).stage() >= 6;
    }

    static void startBattle(CharacterManager manager) {
        requireMap(manager);
        if (manager.getBattleManager() != null
                || manager.getPalletStory().viridianCatchBattle != null) {
            throw new IllegalStateException("捕获教学战斗已经存在");
        }
        MapData map = manager.getCurrentMapDatas()[0];
        ViridianCatchCatalog catalog = manager.getScriptManager().getViridianCatchStory();
        PokemonData wild = PokemonManager.createWildPokemon(
                0,
                "",
                manager.getCharacterData().getPlayerEntity().getRegionIndexId(),
                map.getRomMapHeaderIndex(),
                (short) catalog.wildSpecies(),
                catalog.wildLevel(),
                PokemonContainerType.EVENT.getType(),
                (short) 0,
                manager.getRandom(),
                manager.getSnowflakeIdGenerator());
        if (wild == null) {
            throw new IllegalStateException("捕获教学宝可梦生成失败");
        }
        BattleManager battle = BattleGenerator.generatorWildBattle(
                manager.getCharacterSession(), manager.getPartyPokemons(), wild);
        PalletStoryScene.end(manager);
        manager.getPalletStory().viridianCatchBattle = battle;
        manager.setBattleManager(battle);
        try {
            GameSessionPool.addBattleManagerInPool(characterId(manager), battle);
            battle.handleBattleBegin(manager.getCharacterSession(), false, false);
            scheduleAutomaticCapture(manager, battle);
            log.info("常磐市捕获教学战斗已启动: 角色编号={}, 宝可梦={}, 等级={}",
                    characterId(manager), catalog.wildSpecies(), catalog.wildLevel());
        } catch (RuntimeException exception) {
            manager.setBattleManager(null);
            manager.getPalletStory().viridianCatchBattle = null;
            GameSessionPool.removeBattleManagerInPool(battle);
            throw new IllegalStateException("捕获教学战斗启动失败", exception);
        }
    }

    private static void scheduleAutomaticCapture(CharacterManager manager, BattleManager battle) {
        var session = manager.getCharacterSession();
        AUTO_CAPTURE.schedule(() -> PalletOpeningService.guard(manager, session, () -> {
            if (manager.getCharacterSession() != session
                    || manager.getBattleManager() != battle
                    || manager.getPalletStory().viridianCatchBattle != battle) {
                return;
            }
            byte faction = battle.getFactionIndexBySession(session);
            if (faction < 0 || !battle.isFactionInBattle(faction)) {
                return;
            }
            if (!battle.handlePlayerUseItem(ViridianCatchCatalog.BALL_ITEM_ID, 0, faction, (byte) 0)) {
                throw new IllegalStateException("捕获教学自动抛球动作提交失败");
            }
            log.info("常磐市捕获教学已自动提交精灵球: 角色编号={}, 道具={}",
                    characterId(manager), ViridianCatchCatalog.BALL_ITEM_ID);
        }), 800, TimeUnit.MILLISECONDS);
    }

    private static void play(CharacterManager manager, String key, Runnable next) {
        ViridianCatchCatalog catalog = manager.getScriptManager().getViridianCatchStory();
        long actorId = PalletStoryNpcs.actor(manager, 3).getEntityGameId();
        PalletStoryScene.prompt(manager, actorId, catalog.text(key), false, ignored -> next.run());
    }

    private static void begin(CharacterManager manager) {
        manager.getInteractManager().setMailWidgetOpen(false);
        PalletStoryScene.begin(manager);
    }

    private static boolean idle(CharacterManager manager) {
        return manager.getInteractManager().getInteractType() == InteractType.NONE
                && manager.getBattleManager() == null && !TradeManager.isInTrade(manager);
    }

    private static void requireMap(CharacterManager manager) {
        if (!PalletStoryNpcs.map(manager, MAP)) {
            throw new IllegalStateException("角色已离开常磐市捕获教学地图");
        }
    }

    private static long characterId(CharacterManager manager) {
        return PalletOpeningService.characterId(manager);
    }

    private static int accountId(CharacterManager manager) {
        return manager.getCharacterData().getAccountId();
    }

    private static ViridianCatchStore store(CharacterManager manager) {
        return manager.getCharacterService().getViridianCatchStore();
    }
}
