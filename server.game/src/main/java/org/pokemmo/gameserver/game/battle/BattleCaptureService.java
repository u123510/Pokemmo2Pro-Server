package org.pokemmo.gameserver.game.battle;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapZoneType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.SendAddPokemonPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleCapturePacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendInventoryItemAmountPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendRemoveInventoryItemPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendUpdatePlayerInfo;
import org.pokemmo.gameserver.game.character.UpdateCharacterSelector;
import org.pokemmo.gameserver.services.GameServerService;
import org.pokemmo.gameserver.services.pokemon.PokemonCaptureService;
import org.server.Session;

/** Handles the battle-facing part of a wild ball action. */
@Slf4j
final class BattleCaptureService extends BattleContextComponent {
    private final Gen5CaptureCalculator calculator = new Gen5CaptureCalculator();

    BattleCaptureService(BattleContextState context) {
        super(context);
    }

    boolean settle(BattlePokemonData actionPokemon) {
        if (actionPokemon == null || battleBasisInfo.getBattleType() != BattleType.WildBattle
                || actionPokemon.getTargetPokemon() == null) {
            return false;
        }
        ItemData item = ItemManager.getItemData(actionPokemon.getRoundChoiceItemIndexId());
        if (item == null || !item.isCaptureBall()) {
            return false;
        }
        Session session = actionPokemon.getOwnerSession();
        if (session == null) {
            return false;
        }
        CharacterManager manager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        if (manager == null || manager.getCharacterData() == null
                || manager.getCharacterData().getPlayerEntity() == null) {
            return false;
        }

        PokemonData wildPokemon = actionPokemon.getTargetPokemon().getPokemonData();
        long characterId = manager.getCharacterData().getPlayerEntity().getEntityGameId();
        GameServerService service = manager.getCharacterService();
        var dexData = service.getPokemonDexUnlockDataById(characterId);
        var caughtDex = dexData.length > 2 && dexData[2] != null ? dexData[2] : new java.util.BitSet();
        int highestPlayerLevel = 1;
        for (PokemonData pokemon : manager.getPartyPokemons()) {
            if (pokemon != null) {
                highestPlayerLevel = Math.max(highestPlayerLevel, pokemon.getLevel());
            }
        }
        Gen5CaptureCalculator.Environment environment = new Gen5CaptureCalculator.Environment(
                captureFacility(manager),
                captureTurnsPassed(),
                highestPlayerLevel,
                caughtDex.cardinality(),
                caughtDex.get(Short.toUnsignedInt(wildPokemon.getPokemonIndexId())),
                false,
                false);
        Gen5CaptureCalculator.CaptureResult capture = calculator.calculate(
                actionPokemon, wildPokemon, item,
                manager.getScriptManager() == null ? null
                        : manager.getScriptManager().getCaptureBallRateManager(),
                environment, random);
        byte animationResult = capture.animationResult();
        boolean caught = capture.caught();
        boolean safari = item.getItemBallType() == 4
                && manager.getCharacterData().getSafariBallAmount() > 0
                && manager.getCurrentMapDatas()[0] != null
                && manager.getCurrentMapDatas()[0].getMapKey() != null
                && manager.getCurrentMapDatas()[0].getMapKey().startsWith("SafariZone_");
        var result = safari
                ? service.captureSafariPokemon(characterId, wildPokemon,
                actionPokemon.getRoundChoiceItemIndexId(), caught)
                : service.captureWildPokemon(characterId, wildPokemon,
                actionPokemon.getRoundChoiceItemIndexId(), caught);
        log.debug("野生捕获结算: characterId={}, pokemonId={}, ball={}, ballType={}, modifiedCatchRate={}, result={}, success={}, consumed={}, container={}, position={}",
                characterId, wildPokemon.getPokemonId(), actionPokemon.getRoundChoiceItemIndexId(),
                Byte.toUnsignedInt(item.getItemBallType()), capture.modifiedCatchRate(),
                animationResult, result.success(), result.ballConsumed(),
                result.container(), result.position());
        if (!result.success()) {
            if (result.ballConsumed()) {
                broadcastCaptureResult(actionPokemon, animationResult,
                        result.container(), result.position());
                if (result.safariBallConsumed()) {
                    consumeSafariBallInMemory(manager);
                    sendSafariBallUpdate(session, manager);
                } else {
                    sendConsumedBallUpdate(session, result.consumedBall());
                }
            } else {
                log.warn("野生捕获事务被拒绝，未发送捕获动画或扣球: characterId={}, pokemonId={}, ball={}, trainerId={}",
                        characterId, wildPokemon.getPokemonId(),
                        actionPokemon.getRoundChoiceItemIndexId(), wildPokemon.getTrainerId());
            }
            return false;
        }

        if (result.container() == PokemonContainerType.PARTY) {
            manager.getPartyPokemons()[result.position()] = result.pokemon();
        }

        broadcastCaptureResult(actionPokemon, animationResult,
                result.container(), result.position());
        session.send(new SendAddPokemonPacket(result.pokemon()));
        if (result.safariBallConsumed()) {
            consumeSafariBallInMemory(manager);
            sendSafariBallUpdate(session, manager);
        } else {
            sendConsumedBallUpdate(session, result.consumedBall());
        }

        byte winner = actionPokemon.getDebutFactionIndex();
        byte loser = getEnemyFactionIndex(winner);
        debutFactions.get(winner).setFactionStatType(FactionResultType.CATCH_POKEMON);
        debutFactions.get(loser).setFactionStatType(FactionResultType.DEFEAT);
        return true;
    }

    private void sendConsumedBallUpdate(
            Session session, PokemonCaptureService.ConsumedBall consumedBall) {
        if (consumedBall == null) {
            return;
        }
        if (consumedBall.remainingAmount() > 0) {
            session.send(new SendInventoryItemAmountPacket(
                    consumedBall.itemId(), consumedBall.remainingAmount()));
            return;
        }
        session.send(new SendRemoveInventoryItemPacket(
                consumedBall.inventoryId(), consumedBall.itemId()));
    }

    private void sendSafariBallUpdate(Session session, CharacterManager manager) {
        session.send(new SendUpdatePlayerInfo(
                new UpdateCharacterSelector.Builder()
                        .setCharacterData(manager.getCharacterData())
                        .setRefreshSafariInfo(true)
                        .build()));
    }

    private void consumeSafariBallInMemory(CharacterManager manager) {
        short current = manager.getCharacterData().getSafariBallAmount();
        manager.getCharacterData().setSafariBallAmount((short) Math.max(0, current - 1));
    }

    private BattleFacilityType captureFacility(CharacterManager manager) {
        if (battleBasisInfo.getBattleFacilityType() == BattleFacilityType.GRASS_DARK) {
            return BattleFacilityType.GRASS_DARK;
        }
        MapData map = manager.getCurrentMapDatas() == null || manager.getCurrentMapDatas().length == 0
                ? null : manager.getCurrentMapDatas()[0];
        if (map != null && map.getMapZoneType() == MapZoneType.UNDERWATER) {
            return BattleFacilityType.UNDERWATER;
        }
        if (map != null && map.getMapZoneType() == MapZoneType.UNDERGROUND) {
            return BattleFacilityType.CAVE;
        }
        if (map != null && map.getMapZoneType() == MapZoneType.INSIDE) {
            return BattleFacilityType.INSIDE;
        }
        // Wild encounters reached while surfing use the Dive Ball water bonus.
        if ((manager.getCharacterData().getPlayerEntity().getTransportation() & 1) != 0) {
            return BattleFacilityType.WATER;
        }
        return battleBasisInfo.getBattleFacilityType() == null
                ? BattleFacilityType.ROUTE : battleBasisInfo.getBattleFacilityType();
    }

    /**
     * The battle engine performs one setup/NPC settlement before exposing the
     * first player action, so its round counter is one-based at capture time.
     * Gen V ball rules count turns that have already elapsed instead.
     */
    private int captureTurnsPassed() {
        return Math.max(0, battleBasisInfo.getBattleRoundAmount() - 1);
    }

    private void broadcastCaptureResult(BattlePokemonData actionPokemon, byte result,
                                        PokemonContainerType container, short position) {
        for (Session battleSession : getBattlePlayerSessions()) {
            battleSession.send(new SendBattleCapturePacket(
                    actionPokemon.getSelectorData(), actionPokemon.getRoundChoiceItemIndexId(),
                    result, container, position));
        }
    }
}
