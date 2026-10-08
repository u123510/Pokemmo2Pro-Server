package org.pokemmo.gameserver.game.interact;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.item.KantoItemCatalog;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.story.PalletOpeningService;
import org.pokemmo.gameserver.game.story.PalletStoryNpcs;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryPacket;

/** Handles generated one-time item NPCs after chapter-specific story handlers. */
public final class ItemInteractionService {
    private ItemInteractionService() {
    }

    public static boolean tryCollect(CharacterManager manager, NpcEntity npc) {
        if (manager == null || npc == null || manager.getScriptManager().getKantoItemCatalog() == null) {
            return false;
        }
        if (manager.getPalletStory().isProgressLoadFailed()) {
            PalletOpeningService.notify(manager, "剧情存档尚未读取完成，暂时无法领取地图道具。");
            return true;
        }
        MapData map = manager.getCurrentMapDatas()[0];
        if (map == null) return false;
        KantoItemCatalog.Pickup pickup = manager.getScriptManager().getKantoItemCatalog()
                .find(map.getMapKey(), npc.getInteractionScriptName());
        if (pickup == null) return false;
        String key = pickup.hideFlag() == null || pickup.hideFlag().isBlank()
                ? "kanto/item/" + pickup.map() + "/" + pickup.script()
                : pickup.hideFlag();
        if (manager.getPalletStory().hasEventFlag(key)) {
            return true;
        }
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        if (manager.getCharacterService().addInventoryItem(
                characterId, (short) pickup.itemId(), (short) pickup.amount(),
                manager.getSnowflakeIdGenerator().nextId()) == null) {
            PalletOpeningService.notify(manager, "背包空间不足，无法获得这个道具。");
            return true;
        }
        manager.getCharacterService().getStoryEventFlagStore().setEventFlag(characterId, key);
        manager.getPalletStory().addEventFlag(key);
        var inventory = manager.getCharacterService().getInventory();
        manager.getCharacterSession().send(new SendInventoryPacket(
                inventory,
                manager.getCharacterService().getItemsByContainerAndCharacter(characterId, inventory)));
        PalletOpeningService.notify(manager, "获得道具 #" + pickup.itemId() + " × " + pickup.amount());
        PalletStoryNpcs.refresh(manager);
        return true;
    }
}
