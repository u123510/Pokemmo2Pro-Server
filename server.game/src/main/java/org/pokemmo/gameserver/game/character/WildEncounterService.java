package org.pokemmo.gameserver.game.character;

import org.pokemmo.gameserver.game.battle.BattleGenerator;
import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.interact.InteractType;
import org.pokemmo.gameserver.game.map.KantoMetaTileBehaviorType;
import org.pokemmo.gameserver.game.map.KantoregionMapData;
import org.pokemmo.gameserver.game.map.MapCoordinateData;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapZoneType;
import org.pokemmo.gameserver.game.map.NdsMapData;
import org.pokemmo.gameserver.game.map.WildEncounterManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;

/** Applies loaded regional encounter rates to accepted overworld movement. */
final class WildEncounterService {
    private final CharacterManagerState context;

    WildEncounterService(CharacterManagerState context) {
        this.context = context;
    }

    void handle(MapData previousPrimaryMap, MapData currentMap, short x, short y, byte z) {
        if (isHidden()) {
            resetState();
            return;
        }
        if (previousPrimaryMap == null || currentMap == null || !previousPrimaryMap.equals(currentMap)) {
            resetState();
            return;
        }
        if (context.battleManager != null
                || context.interactManager.getInteractType() != InteractType.NONE
                || context.tradeSession != null) {
            return;
        }
        if (x < 0 || y < 0 || x >= currentMap.getMapWidth() || y >= currentMap.getMapHeight()) {
            return;
        }
        WildEncounterManager encounterManager = context.scriptManager.getWildEncounterManager();
        if (encounterManager == null) {
            return;
        }
        WildEncounterManager.EncounterArea area = getEncounterArea(currentMap, x, y);
        if (area == null) {
            context.setPreviousWildEncounterBehavior((byte) -1);
            return;
        }
        int encounterRate = encounterManager.getEncounterRate(currentMap, area);
        if (encounterRate <= 0) {
            return;
        }

        byte behavior = encounterBehavior(currentMap, x, y, area);
        boolean behaviorChanged = context.getPreviousWildEncounterBehavior() != behavior;
        context.setPreviousWildEncounterBehavior(behavior);
        if (behaviorChanged && context.random.nextInt(100) >= 60) {
            return;
        }
        if (!passesEncounterCooldown(encounterRate)) {
            return;
        }

        int effectiveRate = encounterRate * 16;
        if ((context.characterData.getPlayerEntity().getTransportation() & 2) != 0
                && area == WildEncounterManager.EncounterArea.LAND) {
            effectiveRate = effectiveRate * 80 / 100;
        }
        effectiveRate += context.getWildEncounterRateBuff() * 16 / 200;
        effectiveRate = Math.min(WildEncounterManager.MAX_ENCOUNTER_RATE, effectiveRate);
        if (context.random.nextInt(WildEncounterManager.MAX_ENCOUNTER_RATE) >= effectiveRate) {
            context.setWildEncounterRateBuff(context.getWildEncounterRateBuff() + encounterRate);
            return;
        }

        encounterManager.selectEncounter(currentMap, area, context.random).ifPresent(selection -> {
            startWildBattle(currentMap, selection);
        });
    }

    private byte encounterBehavior(
            MapData map,
            int x,
            int y,
            WildEncounterManager.EncounterArea area
    ) {
        if (map instanceof NdsMapData) {
            return (byte) (area.ordinal() + 1);
        }
        KantoregionMapData gbaMap = (KantoregionMapData) map;
        MapCoordinateData coordinate = gbaMap.getMapCoordinates()[x][y];
        return coordinate.getMetatileBehaviorType().getType();
    }

    private WildEncounterManager.EncounterArea getEncounterArea(MapData map, int x, int y) {
        if (map instanceof NdsMapData ndsMap) {
            if (ndsMap.isGrass(x, y)) {
                return WildEncounterManager.EncounterArea.LAND;
            }
            if ((context.characterData.getPlayerEntity().getTransportation() & 1) != 0
                    && ndsMap.isWater(x, y)) {
                return WildEncounterManager.EncounterArea.WATER;
            }
            return null;
        }
        if (!(map instanceof KantoregionMapData kantoMap)
                || kantoMap.getMapCoordinates() == null) {
            return null;
        }
        MapCoordinateData coordinate = kantoMap.getMapCoordinates()[x][y];
        if (coordinate == null || coordinate.getMetatileBehaviorType() == null) {
            return null;
        }
        KantoMetaTileBehaviorType behavior = coordinate.getMetatileBehaviorType();
        if (behavior == KantoMetaTileBehaviorType.MB_TALL_GRASS
                || (kantoMap.getMapZoneType() == MapZoneType.UNDERGROUND
                && isCaveLandBehavior(behavior))) {
            return WildEncounterManager.EncounterArea.LAND;
        }
        int transportation = context.characterData.getPlayerEntity().getTransportation();
        if ((transportation & 1) != 0 && isWaterBehavior(behavior)) {
            return WildEncounterManager.EncounterArea.WATER;
        }
        return null;
    }

    private void startWildBattle(
            MapData map,
            WildEncounterManager.WildEncounterSelection selection
    ) {
        PokemonData wildPokemon = PokemonManager.createWildPokemon(
                0,
                "",
                context.characterData.getPlayerEntity().getRegionIndexId(),
                map.getRomMapHeaderIndex(),
                (short) selection.pokemonIndexId(),
                selection.level(),
                PokemonContainerType.EVENT.getType(),
                (short) 0,
                context.random,
                context.snowflakeIdGenerator
        );
        if (wildPokemon == null || !hasAlivePartyPokemon()) {
            return;
        }
        BattleManager battleManager = BattleGenerator.generatorWildBattle(
                context.characterSession,
                context.partyPokemons,
                wildPokemon
        );
        resetState();
        context.setBattleManager(battleManager);
        battleManager.handleBattleBegin(context.characterSession, false, false);
    }

    private boolean isCaveLandBehavior(KantoMetaTileBehaviorType behavior) {
        return behavior != KantoMetaTileBehaviorType.MB_POND_WATER
                && behavior != KantoMetaTileBehaviorType.MB_FAST_WATER
                && behavior != KantoMetaTileBehaviorType.MB_DEEP_WATER
                && behavior != KantoMetaTileBehaviorType.MB_WATERFALL
                && behavior != KantoMetaTileBehaviorType.MB_OCEAN_WATER
                && behavior != KantoMetaTileBehaviorType.MB_PUDDLE
                && behavior != KantoMetaTileBehaviorType.MB_SHALLOW_WATER
                && behavior != KantoMetaTileBehaviorType.MB_UNUSED_WATER
                && behavior != KantoMetaTileBehaviorType.MB_CYCLING_ROAD_WATER
                && behavior != KantoMetaTileBehaviorType.MB_SEAWEED;
    }

    private boolean isWaterBehavior(KantoMetaTileBehaviorType behavior) {
        return switch (behavior) {
            case MB_POND_WATER, MB_FAST_WATER, MB_DEEP_WATER, MB_OCEAN_WATER,
                    MB_PUDDLE, MB_SHALLOW_WATER, MB_UNUSED_WATER, MB_CYCLING_ROAD_WATER,
                    MB_SEAWEED -> true;
            default -> false;
        };
    }

    private boolean passesEncounterCooldown(int encounterRate) {
        int minimumSteps = encounterRate >= 80 ? 0 : encounterRate < 10 ? 8 : 8 - encounterRate / 10;
        if (context.getWildEncounterStepsSinceLastEncounter() >= minimumSteps) {
            return true;
        }
        context.setWildEncounterStepsSinceLastEncounter(
                context.getWildEncounterStepsSinceLastEncounter() + 1
        );
        return context.random.nextInt(100) < 5;
    }

    private boolean hasAlivePartyPokemon() {
        for (PokemonData pokemon : context.partyPokemons) {
            if (pokemon != null && pokemon.getCurrentHp() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isHidden() {
        return context.getCharacterData() != null
                && context.getCharacterData().getPlayerEntity() != null
                && context.getPlayerVisibility().isHidden(
                context.getCharacterData().getPlayerEntity().getEntityGameId()
        );
    }

    private void resetState() {
        context.setWildEncounterRateBuff(0);
        context.setWildEncounterStepsSinceLastEncounter(0);
        context.setPreviousWildEncounterBehavior((byte) -1);
    }
}
