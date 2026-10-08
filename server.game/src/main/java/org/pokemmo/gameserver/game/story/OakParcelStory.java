package org.pokemmo.gameserver.game.story;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendLoadDexPacket;
import org.pokemmo.gameserver.services.story.OakParcelStore;

/** The Viridian parcel, return to Oak, and Pokedex/five-ball chapter. */
@Slf4j
public final class OakParcelStory {
    public static final String MART = "ViridianCity_Mart";

    private OakParcelStory() { }

    public static boolean enabled(CharacterManager manager) {
        return manager != null && manager.getScriptManager() != null
                && manager.getScriptManager().getOakParcelStory() != null
                && manager.getScriptManager().getOakParcelStory().enabled();
    }

    static void onLogin(CharacterManager manager) {
        manager.getPalletStory().parcelStatus = null;
        if (!enabled(manager) || manager.getPalletStory().progressLoadFailed) return;
        synchronized (manager.getInteractManager()) {
            try {
                OakParcelProgress progress = manager.getBattleManager() == null
                        ? store(manager).loadProgress(accountId(manager), characterId(manager))
                        : OakParcelProgress.from(manager.getCharacterData());
                apply(manager, progress);
                log.info("大木包裹剧情进度已加载: 角色编号={}, 研究所阶段={}, 包裹状态={}, 下一阶段={}",
                        characterId(manager), progress.labStage(), progress.parcelStatus(), progress.phaseName());
            } catch (RuntimeException exception) {
                manager.getPalletStory().parcelStatus = null;
                log.error("大木包裹剧情读取失败，保留角色资产并暂停本章: 角色编号={}", characterId(manager), exception);
            }
        }
    }

    static void onMapReady(CharacterManager manager) {
        if (!enabled(manager) || !idle(manager)
                || (!PalletStoryNpcs.map(manager, MART) && !PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB))) return;
        PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
            if (!idle(manager)) return;
            OakParcelProgress progress = progress(manager);
            if (PalletStoryNpcs.map(manager, MART) && needsParcel(manager, progress)) {
                pickup(manager);
            } else if (PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB)
                    && progress.phase() == OakParcelProgress.Phase.POKEDEX) {
                begin(manager);
                offerPokedex(manager);
            }
        });
    }

    /** Only an outstanding task preempts the shop; a clerk holding no task stays a shop. */
    static boolean tryQuest(CharacterManager manager, NpcEntity npc) {
        if (!enabled(manager) || (!isClerk(manager, npc) && !isOak(manager, npc))) return false;
        synchronized (manager.getInteractManager()) {
            if (!idle(manager)) return false;
            try {
                OakParcelProgress progress = progress(manager);
                if (isClerk(manager, npc)) {
                    if (!needsParcel(manager, progress)) return false;
                    PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> pickup(manager));
                    return true;
                }
                if (progress.phase() != OakParcelProgress.Phase.DELIVER && progress.phase() != OakParcelProgress.Phase.POKEDEX) return false;
                PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
                    begin(manager);
                    if (progress.phase() == OakParcelProgress.Phase.DELIVER) deliver(manager);
                    else offerPokedex(manager);
                });
                return true;
            } catch (RuntimeException exception) {
                PalletOpeningService.report(manager, exception);
                return true;
            }
        }
    }

    static boolean tryAdvice(CharacterManager manager, NpcEntity npc) {
        if (!enabled(manager) || !isOak(manager, npc)) return false;
        if (manager.getPalletStory().progress == null || manager.getPalletStory().progressLoadFailed
                || manager.getPalletStory().progress.stage() < PalletStoryProgress.COMPLETE) return false;
        PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
            if (!idle(manager)) return;
            begin(manager);
            String text = progress(manager).phase() == OakParcelProgress.Phase.COMPLETE ? "returnAdvice" : "goToCity";
            play(manager, 3, List.of(text), () -> PalletStoryScene.end(manager));
        });
        return true;
    }

    static boolean showRival(CharacterManager manager) {
        if (!enabled(manager) || manager.getPalletStory().parcelStatus == null
                || manager.getPalletStory().progress == null || manager.getPalletStory().progressLoadFailed) return false;
        var phase = new OakParcelProgress(manager.getPalletStory().progress.stage(), manager.getPalletStory().parcelStatus).phase();
        return phase == OakParcelProgress.Phase.DELIVER || phase == OakParcelProgress.Phase.POKEDEX;
    }

    private static boolean needsParcel(CharacterManager manager, OakParcelProgress progress) {
        return progress.phase() == OakParcelProgress.Phase.PICKUP
                || (progress.phase() == OakParcelProgress.Phase.DELIVER && !store(manager).hasParcel(accountId(manager), characterId(manager)));
    }

    private static void pickup(CharacterManager manager) {
        requireMap(manager, MART);
        begin(manager);
        play(manager, 0, List.of("martGreeting", "martRequest"), () -> {
            OakParcelStore.Result result = commit(manager, MART, OakParcelStore.Action.PICKUP);
            if (!result.changed()) { PalletStoryScene.end(manager); return; }
            play(manager, 0, List.of("parcelReceived", "martThanks"), () -> PalletStoryScene.end(manager));
        });
    }

    private static void deliver(CharacterManager manager) {
        play(manager, 3, List.of("oakGreeting"), () -> {
            OakParcelStore.Result result = commit(manager, PalletStoryNpcs.LAB, OakParcelStore.Action.DELIVER);
            if (result.progress().phase() == OakParcelProgress.Phase.COMPLETE) { PalletStoryScene.end(manager); return; }
            if (result.changed()) play(manager, 3, List.of("parcelDelivered", "oakThanks"), () -> offerPokedex(manager));
            else offerPokedex(manager);
        });
    }

    private static void offerPokedex(CharacterManager manager) {
        requireMap(manager, PalletStoryNpcs.LAB);
        play(manager, 3, List.of("dexRequest", "dexDescription", "dexOffer"), () -> {
            OakParcelStore.Result result = commit(manager, PalletStoryNpcs.LAB, OakParcelStore.Action.RECEIVE_POKEDEX);
            if (!result.changed()) { PalletStoryScene.end(manager); return; }
            manager.getCharacterSession().send(new SendLoadDexPacket(
                    manager.getCharacterService().getPokemonDexUnlockDataById(characterId(manager))));
            play(manager, 3, List.of("dexReceived", "ballIntroduction", "ballsReceived", "catchAdvice", "oakDream", "depart"),
                    () -> PalletStoryScene.end(manager));
        });
    }

    static OakParcelStore.Result commit(CharacterManager manager, String map, OakParcelStore.Action action) {
        requireMap(manager, map);
        OakParcelStore.Result result = store(manager).execute(accountId(manager), characterId(manager), action,
                manager.getSnowflakeIdGenerator().nextId());
        apply(manager, result.progress());
        manager.getCharacterSession().send(new SendInventoryPacket(result.inventory(), result.items()));
        PalletStoryNpcs.refresh(manager);
        String operation = switch (action) { case PICKUP -> "领取包裹"; case DELIVER -> "交付包裹"; case RECEIVE_POKEDEX -> "领取图鉴及精灵球"; };
        log.info("大木包裹剧情操作已提交: 角色编号={}, 操作={}, 实际变更={}, 研究所阶段={}, 包裹状态={}",
                characterId(manager), operation, result.changed(), result.progress().labStage(), result.progress().parcelStatus());
        return result;
    }

    static void apply(CharacterManager manager, OakParcelProgress progress) {
        progress.apply(manager.getCharacterData());
        manager.getPalletStory().parcelStatus = progress.parcelStatus();
        manager.getPalletStory().progress = PalletStoryProgress.from(manager.getCharacterData());
    }

    static OakParcelProgress progress(CharacterManager manager) {
        Short parcel = manager.getPalletStory().parcelStatus;
        if (parcel == null) throw new IllegalStateException("大木包裹存档尚未加载，请重新登录");
        // The opening can finish during this login, so do not cache its old stage in this chapter.
        return new OakParcelProgress(PalletOpeningService.progress(manager).stage(), parcel);
    }

    private static void play(CharacterManager manager, int npcIndex, List<String> text, Runnable next) {
        if (text.isEmpty()) { next.run(); return; }
        long actorId = PalletStoryNpcs.actor(manager, npcIndex).getEntityGameId();
        int offset = manager.getScriptManager().getOakParcelStory().text(text.get(0));
        PalletStoryScene.prompt(manager, actorId, offset, false, ignored -> play(manager, npcIndex, text.subList(1, text.size()), next));
    }

    private static void begin(CharacterManager manager) {
        manager.getInteractManager().setMailWidgetOpen(false);
        PalletStoryScene.begin(manager);
    }

    private static boolean idle(CharacterManager manager) {
        return manager.getInteractManager().getInteractType() == InteractType.NONE
                && manager.getBattleManager() == null && !TradeManager.isInTrade(manager);
    }

    static boolean isClerk(CharacterManager manager, NpcEntity npc) {
        return npc != null && PalletStoryNpcs.map(manager, MART) && "npc_0".equals(npc.getNpcName());
    }

    static boolean isOak(CharacterManager manager, NpcEntity npc) {
        return npc != null && PalletStoryNpcs.map(manager, PalletStoryNpcs.LAB) && "npc_3".equals(npc.getNpcName());
    }

    private static void requireMap(CharacterManager manager, String key) {
        if (!PalletStoryNpcs.map(manager, key)) throw new IllegalStateException("角色已离开剧情所在地图");
    }

    private static long characterId(CharacterManager manager) { return PalletOpeningService.characterId(manager); }
    private static int accountId(CharacterManager manager) { return manager.getCharacterData().getAccountId(); }
    private static OakParcelStore store(CharacterManager manager) { return manager.getCharacterService().getOakParcelStore(); }
}
