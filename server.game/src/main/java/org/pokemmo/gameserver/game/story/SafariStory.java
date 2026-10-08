package org.pokemmo.gameserver.game.story;

import com.google.gson.JsonObject;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;

/** Owns the small amount of persistent state needed by the Kanto Safari Zone. */
final class SafariStory {
    private static final String ENTRANCE = "FuchsiaCity_SafariZone_Entrance";
    private static final String OUTDOOR_PREFIX = "SafariZone_";

    private SafariStory() {
    }

    static void onMapReady(CharacterManager manager) {
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null) {
            return;
        }
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null || isSafariMap(map.getMapKey())) {
            return;
        }
        if (manager.getCharacterData().getSafariSteps() > 0
                || manager.getCharacterData().getSafariBallAmount() > 0) {
            clear(manager);
        }
    }

    static boolean onStep(CharacterManager manager) {
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null || !isOutdoorSafariMap(map.getMapKey())) {
            return false;
        }
        short current = manager.getCharacterData().getSafariSteps();
        if (current <= 0) {
            return false;
        }
        short remaining = (short) (current - 1);
        manager.getCharacterData().setSafariSteps(remaining);
        manager.getCharacterService().updateSafariState(
                PalletOpeningService.characterId(manager),
                remaining,
                manager.getCharacterData().getSafariBallAmount());
        refresh(manager);
        if (remaining != 0) {
            return false;
        }
        PalletOpeningService.notify(manager, "野生原野区的探索时间到了。");
        JsonObject action = new JsonObject();
        action.addProperty("map", ENTRANCE);
        action.addProperty("x", 4);
        action.addProperty("y", 7);
        action.addProperty("z", 0);
        action.addProperty("toward", 1);
        StoryRuntime.changeMap(manager, action);
        return true;
    }

    private static boolean isSafariMap(String mapKey) {
        return mapKey != null && (mapKey.startsWith(OUTDOOR_PREFIX)
                || mapKey.equals(ENTRANCE));
    }

    private static boolean isOutdoorSafariMap(String mapKey) {
        return mapKey != null && mapKey.startsWith(OUTDOOR_PREFIX)
                && !mapKey.contains("_RestHouse")
                && !mapKey.contains("_SecretHouse");
    }

    private static void clear(CharacterManager manager) {
        manager.getCharacterData().setSafariSteps((short) 0);
        manager.getCharacterData().setSafariBallAmount((short) 0);
        manager.getCharacterService().updateSafariState(
                PalletOpeningService.characterId(manager), (short) 0, (short) 0);
        refresh(manager);
    }

    private static void refresh(CharacterManager manager) {
        manager.getCharacterSession().send(new SendUpdatePlayerInfo(
                new UpdateCharacterSelector.Builder()
                        .setCharacterData(manager.getCharacterData())
                        .setRefreshSafariInfo(true)
                        .build()));
    }
}
