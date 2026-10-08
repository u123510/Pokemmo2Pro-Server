package org.pokemmo.gameserver.game.story;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.TrainerInteractionService;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.server.Session;

/** Routes independent chapters while keeping one native interaction session per character. */
public final class StoryService {
    private StoryService() { }

    public static boolean enabled(CharacterManager manager) {
        if (StoryRuntime.enabled(manager)) return true;
        return PalletOpeningService.enabled(manager) || OakParcelStory.enabled(manager)
                || ViridianCatchStory.enabled(manager);
    }

    public static void onLogin(CharacterManager manager) {
        if (StoryRuntime.enabled(manager)) {
            StoryRuntime.onLogin(manager);
            return;
        }
        PalletOpeningService.onLogin(manager);
        OakParcelStory.onLogin(manager);
        ViridianCatchStory.onLogin(manager);
    }

    public static void onMapReady(CharacterManager manager) {
        SafariStory.onMapReady(manager);
        if (StoryRuntime.enabled(manager)) {
            StoryRuntime.onMapReady(manager);
            OakParcelStory.onMapReady(manager);
            ViridianCatchStory.onMapReady(manager);
            return;
        }
        PalletOpeningService.onMapReady(manager);
        OakParcelStory.onMapReady(manager);
        ViridianCatchStory.onMapReady(manager);
    }

    public static boolean onStep(CharacterManager manager, int x, int y) {
        if (SafariStory.onStep(manager)) {
            return true;
        }
        if (StoryRuntime.enabled(manager)) {
            if (StoryRuntime.onStep(manager, x, y)) {
                return true;
            }
            return PalletOpeningService.onStep(manager, x, y);
        }
        return PalletOpeningService.onStep(manager, x, y);
    }

    public static boolean beforeShop(CharacterManager manager, NpcEntity npc) {
        if (StoryRuntime.enabled(manager)) {
            return StoryRuntime.beforeShop(manager, npc) || OakParcelStory.tryQuest(manager, npc);
        }
        return OakParcelStory.tryQuest(manager, npc);
    }

    public static boolean onNpc(CharacterManager manager, NpcEntity npc) {
        if (StoryRuntime.enabled(manager)) {
            if (StoryRuntime.onNpc(manager, npc)) {
                return true;
            }
            return OakParcelStory.tryAdvice(manager, npc)
                    || ViridianCatchStory.onNpc(manager, npc)
                    || PokemonCenterNurseStory.onNpc(manager, npc)
                    || PalletOpeningService.onNpc(manager, npc);
        }
        return OakParcelStory.tryAdvice(manager, npc)
                || ViridianCatchStory.onNpc(manager, npc)
                || PokemonCenterNurseStory.onNpc(manager, npc)
                || PalletOpeningService.onNpc(manager, npc);
    }

    public static void onReply(CharacterManager manager, Session session, byte sequence, byte choice) {
        if (StoryRuntime.enabled(manager)) {
            StoryRuntime.onReply(manager, session, sequence, choice);
            if (manager.getPalletStory().activeStoryId != null
                    || manager.getPalletStory().continuation != null) {
                return;
            }
            if (PokemonCenterNurseStory.onReply(manager, session, sequence, choice)) {
                return;
            }
            PalletOpeningService.onReply(manager, session, sequence, choice);
            return;
        }
        if (!PokemonCenterNurseStory.onReply(manager, session, sequence, choice)) {
            PalletOpeningService.onReply(manager, session, sequence, choice);
        }
    }

    public static void onDisconnect(CharacterManager manager, Session session) {
        if (StoryRuntime.enabled(manager)) {
            StoryRuntime.onDisconnect(manager, session);
            return;
        }
        PalletOpeningService.onDisconnect(manager, session);
    }

    public static void onBattleFinished(CharacterManager manager, BattleManager battle) {
        if (StoryRuntime.enabled(manager)) {
            StoryRuntime.onBattleFinished(manager, battle);
        }
        PalletStoryBattle.settle(manager, battle);
        ViridianCatchStory.settle(manager, battle);
        TrainerInteractionService.onBattleFinished(manager, battle);
    }

    public static boolean onFinishReply(CharacterManager manager) {
        if (StoryRuntime.enabled(manager)) {
            if (manager.getPalletStory().storyBattleId != null) {
                return StoryRuntime.onFinishReply(manager);
            }
        }
        if (ViridianCatchStory.onFinishReply(manager)) return true;
        return PalletStoryBattle.onFinishReply(manager);
    }
}
