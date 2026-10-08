package org.pokemmo.gameserver.game.pokemon;


public class PokemonEvolutionConfig {
    private short id;
    private String name;
    private String type;
    private short val;
    public PokemonEvolution toPokemonEvolution(){
        return new PokemonEvolution(id,toPokemonEvolutionConditionType(), val);
    }
    private PokemonEvolutionConditionType toPokemonEvolutionConditionType(){
       switch (this.type) {
           case "BREEDING_ONLY":
               return PokemonEvolutionConditionType.BREEDING_ONLY;
           case "HAPPINESS":
               return PokemonEvolutionConditionType.HAPPINESS;
           case "HAPPINESS_DAY":
               return PokemonEvolutionConditionType.HAPPINESS_DAY;
           case "HAPPINESS_NIGHT":
               return PokemonEvolutionConditionType.HAPPINESS_NIGHT;
           case "LEVEL":
               return PokemonEvolutionConditionType.LEVEL;
           case "TRADE":
               return PokemonEvolutionConditionType.TRADE;
           case "TRADE_WITH_ITEM":
               return PokemonEvolutionConditionType.TRADE_WITH_ITEM;
           case "TRADE_FOR_OPPOSITE":
               return PokemonEvolutionConditionType.TRADE_FOR_OPPOSITE;
           case "ITEM":
               return PokemonEvolutionConditionType.ITEM;
           case "ATK_GREATER_THAN_DEF":
               return PokemonEvolutionConditionType.ATK_GREATER_THAN_DEF;
           case "ATK_EQUAL_TO_DEF":
               return PokemonEvolutionConditionType.ATK_EQUAL_TO_DEF;
           case "ATK_LESS_THAN_DEF":
               return PokemonEvolutionConditionType.ATK_LESS_THAN_DEF;
           case "PERSONALITY_HIGH":
               return PokemonEvolutionConditionType.PERSONALITY_HIGH;
           case "PERSONALITY_LOW":
               return PokemonEvolutionConditionType.PERSONALITY_LOW;
           case "ALLOW_MONSTER_CREATION":
               return PokemonEvolutionConditionType.ALLOW_MONSTER_CREATION;
           case "CREATE_EXTRA_MONSTER":
               return PokemonEvolutionConditionType.CREATE_EXTRA_MONSTER;
           case "MAX_BEAUTY":
               return PokemonEvolutionConditionType.MAX_BEAUTY;
           case "ITEM_MALE":
               return PokemonEvolutionConditionType.ITEM_MALE;
           case "ITEM_FEMALE":
               return PokemonEvolutionConditionType.ITEM_FEMALE;
           case "LEVEL_ITEM_DAY":
               return PokemonEvolutionConditionType.LEVEL_ITEM_DAY;
           case "LEVEL_ITEM_NIGHT":
               return PokemonEvolutionConditionType.LEVEL_ITEM_NIGHT;
           case "LEVEL_WITH_SKILL":
               return PokemonEvolutionConditionType.LEVEL_WITH_SKILL;
           case "LEVEL_WITH_MONSTER":
               return PokemonEvolutionConditionType.LEVEL_WITH_MONSTER;
           case "LEVEL_MALE":
               return PokemonEvolutionConditionType.LEVEL_MALE;
           case "LEVEL_FEMALE":
               return PokemonEvolutionConditionType.LEVEL_FEMALE;
           case "LEVEL_LOCATION_1":
               return PokemonEvolutionConditionType.LEVEL_LOCATION_1;
           case "LEVEL_LOCATION_2":
               return PokemonEvolutionConditionType.LEVEL_LOCATION_2;
           case "LEVEL_LOCATION_3":
               return PokemonEvolutionConditionType.LEVEL_LOCATION_3;
           default:
               return null;
       }
    }
}
