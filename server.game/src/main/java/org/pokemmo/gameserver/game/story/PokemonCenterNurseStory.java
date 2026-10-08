package org.pokemmo.gameserver.game.story;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.server.Session;

/** Replays the native Pokemon Center nurse flow and restores the current party. */
@Slf4j
public final class PokemonCenterNurseStory {
    private static final int GREETING_TEXT = 271001323;
    private static final int ACCEPT_TEXT = 271001448;
    private static final int HEALED_TEXT = 271001496;
    private static final int GOODBYE_TEXT = 271001563;

    private PokemonCenterNurseStory() {
    }

    static boolean onNpc(CharacterManager manager, NpcEntity npc) {
        if (!isNurse(manager, npc) || !idle(manager)) {
            return false;
        }
        PalletOpeningService.guard(manager, manager.getCharacterSession(), () -> {
            if (!idle(manager)) {
                return;
            }
            manager.getPalletStory().nurseInteraction = true;
            PalletStoryScene.begin(manager);
            askToHeal(manager, npc.getEntityGameId());
        });
        return true;
    }

    static boolean onReply(CharacterManager manager, Session session, byte sequence, byte choice) {
        if (!manager.getPalletStory().nurseInteraction
                || manager.getInteractManager().getInteractType() != InteractType.STORY) {
            return false;
        }
        PalletOpeningService.guard(manager, session, () -> {
            PalletStoryScene.reply(manager, sequence, Byte.toUnsignedInt(choice));
        });
        return true;
    }

    private static void askToHeal(CharacterManager manager, long nurseId) {
        PalletStoryScene.prompt(manager, nurseId, GREETING_TEXT, true, answer -> {
            if (answer == 0) {
                sayAndEnd(manager, nurseId, GOODBYE_TEXT);
                return;
            }
            PalletStoryScene.sayText(manager, nurseId, ACCEPT_TEXT, () -> heal(manager, nurseId));
        });
    }

    private static void heal(CharacterManager manager, long nurseId) {
        long characterId = PalletOpeningService.characterId(manager);
        var result = manager.getCharacterService().healPartyFully(characterId, manager.getPartyPokemons());
        if (!result.success()) {
            throw new IllegalStateException(result.reason());
        }
        PalletStoryParty.refresh(manager);
        log.info("宝可梦中心治疗完成: 角色编号={}, 恢复数量={}", characterId, result.pokemons().size());
        PalletStoryScene.sayText(manager, nurseId, HEALED_TEXT,
                () -> sayAndEnd(manager, nurseId, GOODBYE_TEXT));
    }

    private static void sayAndEnd(CharacterManager manager, long nurseId, int text) {
        PalletStoryScene.sayText(manager, nurseId, text, () -> PalletStoryScene.end(manager));
    }

    private static boolean isNurse(CharacterManager manager, NpcEntity npc) {
        if (manager == null || npc == null || manager.getCurrentMapDatas()[0] == null) {
            return false;
        }
        String mapKey = manager.getCurrentMapDatas()[0].getMapKey();
        String script = npc.getInteractionScriptName();
        return mapKey != null && mapKey.endsWith("PokemonCenter_1F")
                && script != null && script.endsWith("EventScript_Nurse");
    }

    private static boolean idle(CharacterManager manager) {
        return manager.getInteractManager().getInteractType() == InteractType.NONE
                && manager.getBattleManager() == null
                && !TradeManager.isInTrade(manager);
    }
}
