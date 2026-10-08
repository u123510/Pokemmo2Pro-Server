package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.Session;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BattleGenerator {
    //生成Trainer类型劲敌对战，劲敌对战只可能是单打
    public static BattleManager generatorTrainerRivalBattle(Session hostSession,int trainerRegionIndexId, int trainerNameIndexId,int money,PokemonData[] playerTeamPokemon, PokemonData[] rivalTeamPokemon){
         return generatorTrainerBattle(hostSession, trainerRegionIndexId, trainerNameIndexId, money,
                 playerTeamPokemon, rivalTeamPokemon, BattleType.RivalBattle);
    }

    public static BattleManager generatorTrainerBattle(Session hostSession, int trainerRegionIndexId,
                                                        int trainerNameIndexId, int money,
                                                        PokemonData[] playerTeamPokemon,
                                                        PokemonData[] rivalTeamPokemon,
                                                        BattleType battleType) {
         CharacterData playerCharacterData = hostSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData();
         //获取玩家的对战宝可梦队伍
         List<BattlePokemonData> playerTeamBattlePokemons = convertToBattlePokemonList(playerTeamPokemon,hostSession,0,0);
         PlayerTeam playerTeam = new PlayerTeam(0,6,4,playerTeamBattlePokemons,playerCharacterData,hostSession);
         DebutBattleTeam playerDebutTeam = new DebutBattleTeam(playerTeam, 0, 0);
         BattlePokemonData[] playerDebutPokemons = findTopAlivePokemons(playerTeamBattlePokemons,1);
         FactionData firstFaction = new FactionData(0,playerDebutTeam, playerDebutPokemons,new BattleFactionFieldInfo());
         //获取劲敌的对战宝可梦队伍
         List<BattlePokemonData> rivalTeamBattlePokemons = convertToBattlePokemonList(rivalTeamPokemon,null,1,0);
         TrainerTeam rivalTeam = new TrainerTeam(1,6,4,rivalTeamBattlePokemons,trainerRegionIndexId,trainerNameIndexId,money);
         DebutBattleTeam rivalDebutTeam = new DebutBattleTeam(rivalTeam, 0, 0);
         BattlePokemonData[] rivalDebutPokemons = findTopAlivePokemons(rivalTeamBattlePokemons,1);
         FactionData secondFaction = new FactionData(1,rivalDebutTeam, rivalDebutPokemons,new BattleFactionFieldInfo());
         BattleManager battleManager = new BattleManager.Builder()
                .setBattleType(battleType)
                .setBattleFormatType(BattleFormatType.SINGLE_BATTLE)
                .setBattleFormType(BattleFormType.NORMAL)
                .setBattleFacilityType(BattleFacilityType.INSIDE)
                .setSelfFaction(firstFaction)
                .setEnemyFaction(secondFaction)
                .setBattleAlreadyRunTime(0)
                .build();
         //TODO 不可能所有战斗全部在室内
         return battleManager;
    }

    public static BattleManager generatorWildBattle(Session hostSession, PokemonData[] playerTeamPokemon, PokemonData wildPokemon) {
        CharacterData playerCharacterData = hostSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData();
        List<BattlePokemonData> playerTeamBattlePokemons = convertToBattlePokemonList(
                playerTeamPokemon, hostSession, 0, 0
        );
        PlayerTeam playerTeam = new PlayerTeam(
                0,
                6,
                BattleType.WildBattle.getCanUseItemAmount(),
                playerTeamBattlePokemons,
                playerCharacterData,
                hostSession
        );
        DebutBattleTeam playerDebutTeam = new DebutBattleTeam(playerTeam, 0, 0);
        BattlePokemonData[] playerDebutPokemons = findTopAlivePokemons(playerTeamBattlePokemons, 1);
        FactionData firstFaction = new FactionData(
                0, playerDebutTeam, playerDebutPokemons, new BattleFactionFieldInfo()
        );

        List<BattlePokemonData> wildTeamBattlePokemons = convertToBattlePokemonList(
                new PokemonData[]{wildPokemon}, null, 1, 0
        );
        WildTeam wildTeam = new WildTeam((byte) 1, (byte) 1, wildTeamBattlePokemons);
        DebutBattleTeam wildDebutTeam = new DebutBattleTeam(wildTeam, 0, 0);
        BattlePokemonData[] wildDebutPokemons = findTopAlivePokemons(wildTeamBattlePokemons, 1);
        FactionData secondFaction = new FactionData(
                1, wildDebutTeam, wildDebutPokemons, new BattleFactionFieldInfo()
        );

        return new BattleManager.Builder()
                .setBattleType(BattleType.WildBattle)
                .setBattleFormatType(BattleFormatType.SINGLE_BATTLE)
                .setBattleFormType(BattleFormType.NORMAL)
                .setBattleFacilityType(BattleFacilityType.ROUTE)
                .setSelfFaction(firstFaction)
                .setEnemyFaction(secondFaction)
                .setBattleAlreadyRunTime(0)
                .build();
    }

    public static BattleManager generatorPlayerBattle(Session firstSession, Session secondSession,
                                                      PokemonData[] firstTeamPokemon,
                                                      PokemonData[] secondTeamPokemon,
                                                      BattleFormatType battleFormatType) {
        if (firstSession == null || secondSession == null || battleFormatType != BattleFormatType.SINGLE_BATTLE) {
            return null;
        }
        if (firstSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() == null
                || secondSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get() == null
                || firstTeamPokemon == null || secondTeamPokemon == null) {
            return null;
        }
        CharacterData firstCharacterData = firstSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData();
        CharacterData secondCharacterData = secondSession.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get().getCharacterData();
        if (firstCharacterData == null || secondCharacterData == null) {
            return null;
        }
        List<BattlePokemonData> firstBattlePokemons = convertToBattlePokemonList(
                firstTeamPokemon, firstSession, 0, 0);
        List<BattlePokemonData> secondBattlePokemons = convertToBattlePokemonList(
                secondTeamPokemon, secondSession, 1, 0);
        PlayerTeam firstTeam = new PlayerTeam(0, 6, BattleType.PlayerBattle.getCanUseItemAmount(),
                firstBattlePokemons, firstCharacterData, firstSession);
        PlayerTeam secondTeam = new PlayerTeam(1, 6, BattleType.PlayerBattle.getCanUseItemAmount(),
                secondBattlePokemons, secondCharacterData, secondSession);
        FactionData firstFaction = new FactionData(0,
                new DebutBattleTeam(firstTeam, 0, 0), findTopAlivePokemons(firstBattlePokemons, 1),
                new BattleFactionFieldInfo());
        FactionData secondFaction = new FactionData(1,
                new DebutBattleTeam(secondTeam, 0, 0), findTopAlivePokemons(secondBattlePokemons, 1),
                new BattleFactionFieldInfo());
        return new BattleManager.Builder()
                .setBattleType(BattleType.PlayerBattle)
                .setBattleFormatType(battleFormatType)
                .setBattleFormType(BattleFormType.NORMAL)
                .setBattleFacilityType(BattleFacilityType.INSIDE)
                .setSelfFaction(firstFaction)
                .setEnemyFaction(secondFaction)
                .setBattleAlreadyRunTime(0)
                .build();
    }
    public static BattlePokemonData[] findTopAlivePokemons(List<BattlePokemonData> pokemonList, int count) {
        List<BattlePokemonData> alivePokemons = pokemonList.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getPokemonData() != null && p.getPokemonData().getCurrentHp() > 0)
                .limit(count)
                .toList();

        BattlePokemonData[] result = new BattlePokemonData[count];
        for (int i = 0; i < alivePokemons.size() && i < count; i++) {
            result[i] = alivePokemons.get(i);
        }
        return result;
    }

    //从普通宝可梦列表转换为战斗宝可梦列表
    public static List<BattlePokemonData> convertToBattlePokemonList(PokemonData[] pokemonArray, Session hostSession,int debutFactionIndex,int pokemonTeamIndex) {
    return Arrays.stream(pokemonArray)
                .filter(Objects::nonNull)
                .peek(pokemon -> pokemon.setMaxHp(PokemonManager.calculateMaxHp(pokemon)))
                .map(p -> new BattlePokemonData(
                        hostSession,
                        p,
                        p.getPokemonFirstType(),
                        p.getPokemonSecondType(),
                        p.getPokemonSex(),
                        p.getPokemonAbilityIndexId(),
                        debutFactionIndex,
                        pokemonTeamIndex,
                        p.getContainerPosition(),
                        true
                )).toList();
    }
}
