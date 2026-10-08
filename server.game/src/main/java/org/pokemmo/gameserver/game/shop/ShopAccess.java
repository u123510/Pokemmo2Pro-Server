package org.pokemmo.gameserver.game.shop;

import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.KantoMetaTileBehaviorType;
import org.pokemmo.gameserver.game.map.KantoregionMapData;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.trade.TradeManager;

final class ShopAccess {
    private ShopAccess() {
    }

    static boolean allowed(CharacterManager manager, NpcEntity npc, InteractType expectedState) {
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null || npc == null
                || manager.getInteractManager().getInteractType() != expectedState
                || manager.getBattleManager() != null || TradeManager.isInTrade(manager)
                || npc.getEntityGameId() <= 0 || !npc.isLoad() || !npc.isCanInteract()) {
            return false;
        }
        PlayerEntity player = manager.getCharacterData().getPlayerEntity();
        MapData[] maps = manager.getCurrentMapDatas();
        if (maps == null || maps.length == 0 || maps[0] == null
                || !maps[0].equals(player.getRegionIndexId(),
                player.getMapHeaderIdOrGbaMapGroupId(), player.getGbaMapId())
                || maps[0].getNpcEntityByGameId(npc.getEntityGameId()) != npc) {
            return false;
        }
        return isFacingShop(player, npc, maps[0]);
    }

    private static boolean isFacingShop(PlayerEntity player, NpcEntity npc, MapData map) {
        int dx = npc.getX() - player.getX();
        int dy = npc.getY() - player.getY();
        int distance = Math.abs(dx) + Math.abs(dy);
        if (distance < 1 || distance > 2 || (dx != 0 && dy != 0)) {
            return false;
        }
        boolean facing = switch (player.getToward()) {
            case 0 -> dy > 0;
            case 1 -> dy < 0;
            case 2 -> dx < 0;
            case 3 -> dx > 0;
            default -> false;
        };
        if (!facing) {
            return false;
        }
        if (map instanceof KantoregionMapData gbaMap) {
            if (player.getZ() >= 0 && npc.getZ() >= 0
                    && player.getZ() / 3 != npc.getZ() / 3) {
                return false;
            }
            return distance == 1 || gbaMap.getMetatileBehavior(
                    player.getX() + Integer.signum(dx), player.getY() + Integer.signum(dy))
                    == KantoMetaTileBehaviorType.MB_COUNTER;
        }
        return distance == 1 && player.getZ() == npc.getZ();
    }
}
