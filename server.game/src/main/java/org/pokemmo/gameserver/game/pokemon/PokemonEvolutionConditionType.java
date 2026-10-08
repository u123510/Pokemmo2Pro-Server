package org.pokemmo.gameserver.game.pokemon;

public enum PokemonEvolutionConditionType {
    BREEDING_ONLY(0),
    HAPPINESS(1),
    HAPPINESS_DAY(2),
    HAPPINESS_NIGHT(3),
    LEVEL(4),
    TRADE(5),
    TRADE_WITH_ITEM(6),
    TRADE_FOR_OPPOSITE(7),
    ITEM(8),
    ATK_GREATER_THAN_DEF(9),
    ATK_EQUAL_TO_DEF(10),
    ATK_LESS_THAN_DEF(11),
    PERSONALITY_HIGH(12),
    PERSONALITY_LOW(13),
    ALLOW_MONSTER_CREATION(14),
    CREATE_EXTRA_MONSTER(15),
    MAX_BEAUTY(16),
    ITEM_MALE(17),
    ITEM_FEMALE(18),
    LEVEL_ITEM_DAY(19),
    LEVEL_ITEM_NIGHT(20),
    LEVEL_WITH_SKILL(21),
    LEVEL_WITH_MONSTER(22),
    LEVEL_MALE(23),
    LEVEL_FEMALE(24),
    LEVEL_LOCATION_1(25),
    LEVEL_LOCATION_2(26),
    LEVEL_LOCATION_3(27);
   private byte type;
   PokemonEvolutionConditionType(int type){
       this.type = (byte)type;
   }

   public byte getType() {
       return type;
   }
}
