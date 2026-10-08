package org.pokemmo.gameserver.game.pokemon;

import org.pokemmo.gameserver.game.move.MoveLearnConditionType;

public class PokemonMove {
    private short moveIndexId;
    private MoveLearnConditionType moveLearnConditionType;
    private short learnNeedLevel;
    public PokemonMove(short moveIndexId, MoveLearnConditionType moveLearnConditionType, short learnNeedLevel) {
        this.moveIndexId = moveIndexId;
        this.moveLearnConditionType = moveLearnConditionType;
        this.learnNeedLevel = learnNeedLevel;
    }
    public short getPokemonMoveIndexId() {
        return moveIndexId;
    }
    public MoveLearnConditionType getMoveLearnConditionType() {
        return moveLearnConditionType;
    }
    public short getLearnNeedLevel() {
        return learnNeedLevel;
    }
}
