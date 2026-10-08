package org.pokemmo.gameserver.game.pokemon;


import org.pokemmo.gameserver.game.move.MoveLearnConditionType;
public class PokemonMoveConfig {
    private short id;
    private String name;
    private String type;
    private short level;
    public PokemonMove toPokemonMove()  {
        return new PokemonMove(id,toMoveLearnConditionType(),level);
    }
    private MoveLearnConditionType toMoveLearnConditionType()  {
        switch (this.type){
            case "level":
                return MoveLearnConditionType.LEVEL;
            case "遗传":
                return MoveLearnConditionType.EGG_MOVE;
            case "教学":
                return MoveLearnConditionType.MOVE_TUTOR;
            case "特殊":
                return MoveLearnConditionType.SPECIAL_MOVE;
            case "TM??":
                return MoveLearnConditionType.MOVE_LEARNER_TOOL;
            case "进化":
                return MoveLearnConditionType.EVOLUTION;
            case "进化前":
                return MoveLearnConditionType.PREVO;
            default:
                return null;
        }
    }
}
