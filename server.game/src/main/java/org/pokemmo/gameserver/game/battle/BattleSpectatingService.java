package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.character.PlayerVisibilityService;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.MapConnectionType;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.trade.TradeManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;

/** Validates and manages read-only viewers of active player battles. */
public final class BattleSpectatingService {
    private BattleSpectatingService() {
    }

    public record Result(boolean success, String message) {
    }

    public static Result request(CharacterManager spectator, String targetName) {
        if (spectator == null || spectator.getCharacterData() == null
                || spectator.getCharacterData().getPlayerEntity() == null) {
            return failure("The character is not ready to spectate.");
        }
        if (targetName == null || targetName.isBlank() || targetName.length() > 32
                || targetName.chars().anyMatch(Character::isISOControl)) {
            return failure("Usage: //spectate player_name");
        }
        Session spectatorSession = spectator.getCharacterSession();
        if (spectatorSession == null || !spectatorSession.isActive()) {
            return failure("Your game session is not active.");
        }
        if (spectator.getBattleManager() != null
                || spectator.getInteractManager().getInteractType() != InteractType.NONE
                || TradeManager.isInTrade(spectator)) {
            return failure("You cannot spectate while busy.");
        }

        MapData currentMap = spectator.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
        if (currentMap == null) {
            return failure("You are not currently on a map.");
        }
        CharacterManager target = findTarget(currentMap, spectator, targetName);
        if (target == null) {
            return failure("That player is not nearby.");
        }
        return request(spectator, target);
    }

    public static Result request(CharacterManager spectator, long targetCharacterId) {
        if (spectator == null || spectator.getCharacterData() == null
                || spectator.getCharacterData().getPlayerEntity() == null) {
            return failure("The character is not ready to spectate.");
        }
        if (targetCharacterId <= 0) {
            return failure("The target player ID is invalid.");
        }
        Session spectatorSession = spectator.getCharacterSession();
        if (spectatorSession == null || !spectatorSession.isActive()) {
            return failure("Your game session is not active.");
        }
        if (spectator.getBattleManager() != null
                || spectator.getInteractManager().getInteractType() != InteractType.NONE
                || TradeManager.isInTrade(spectator)) {
            return failure("You cannot spectate while busy.");
        }

        MapData currentMap = spectator.getCurrentMapDatas()[MapConnectionType.NOTHING.getType()];
        if (currentMap == null) {
            return failure("You are not currently on a map.");
        }
        CharacterManager target = findTarget(currentMap, spectator, targetCharacterId);
        if (target == null) {
            return failure("That player is not nearby.");
        }
        return request(spectator, target);
    }

    private static Result request(CharacterManager spectator, CharacterManager target) {
        if (!sameLocation(spectator, target) || !PlayerVisibilityService.canSee(target, spectator)) {
            return failure("The player is not on the same map.");
        }

        Session spectatorSession = spectator.getCharacterSession();

        BattleManager battleManager = target.getBattleManager();
        if (battleManager == null || battleManager.battleBasisInfo.getBattleType() != BattleType.PlayerBattle
                || battleManager.getFactionIndexBySession(target.getCharacterSession()) < 0
                || !battleManager.isFactionInBattle((byte) 0)
                || !battleManager.isFactionInBattle((byte) 1)) {
            return failure("That player is not in an active player battle.");
        }
        if (battleManager.isSpectator(spectatorSession)) {
            return failure("You are already spectating this battle.");
        }
        if (!battleManager.addSpectator(spectatorSession)) {
            return failure("This battle cannot accept another spectator.");
        }
        spectator.setBattleManager(battleManager);
        String targetName = target.getCharacterData().getPlayerEntity().getPlayerName();
        return new Result(true, "Now spectating " + targetName + ".");
    }

    public static boolean leave(CharacterManager spectator) {
        if (spectator == null || spectator.getBattleManager() == null) {
            return false;
        }
        BattleManager battleManager = spectator.getBattleManager();
        if (!battleManager.isSpectator(spectator.getCharacterSession())) {
            return false;
        }
        battleManager.removeSpectator(spectator.getCharacterSession());
        spectator.setBattleManager(null);
        return true;
    }

    private static CharacterManager findTarget(MapData currentMap,
                                               CharacterManager spectator,
                                               String targetName) {
        for (Session candidateSession : currentMap.getPlayerSessionPool().values()) {
            if (candidateSession == null || candidateSession == spectator.getCharacterSession()
                    || !candidateSession.isActive()) {
                continue;
            }
            CharacterManager candidate = candidateSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (candidate == null || candidate.getCharacterData() == null
                    || candidate.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            String candidateName = candidate.getCharacterData().getPlayerEntity().getPlayerName();
            if (candidateName != null && candidateName.equalsIgnoreCase(targetName)) {
                return candidate;
            }
        }
        return null;
    }

    private static CharacterManager findTarget(MapData currentMap,
                                               CharacterManager spectator,
                                               long targetCharacterId) {
        for (Session candidateSession : currentMap.getPlayerSessionPool().values()) {
            if (candidateSession == null || candidateSession == spectator.getCharacterSession()
                    || !candidateSession.isActive()) {
                continue;
            }
            CharacterManager candidate = candidateSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
            if (candidate == null || candidate.getCharacterData() == null
                    || candidate.getCharacterData().getPlayerEntity() == null) {
                continue;
            }
            if (candidate.getCharacterData().getPlayerEntity().getEntityGameId() == targetCharacterId) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean sameLocation(CharacterManager first, CharacterManager second) {
        CharacterData firstData = first.getCharacterData();
        CharacterData secondData = second.getCharacterData();
        return firstData.getChannel() == secondData.getChannel()
                && firstData.getPlayerEntity().getRegionIndexId()
                == secondData.getPlayerEntity().getRegionIndexId()
                && firstData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                == secondData.getPlayerEntity().getMapHeaderIdOrGbaMapGroupId()
                && firstData.getPlayerEntity().getGbaMapId()
                == secondData.getPlayerEntity().getGbaMapId();
    }

    private static Result failure(String message) {
        return new Result(false, message);
    }
}
