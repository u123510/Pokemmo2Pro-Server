package org.pokemmo.gameserver.game.pokemon;

import lombok.Getter;

@Getter
public class PokemonYield {
    private short baseExp;
    private short evHp;
    private short evAttack;
    private short evDefense;
    private short evSpeed;
    private short evSpAttack;
    private short evSpDefense;
    public PokemonYield(short baseExp, short evHp, short evAttack, short evDefense, short evSpeed, short evSpAttack,  short evSpDefense) {
        this.baseExp = baseExp;
        this.evHp = evHp;
        this.evAttack = evAttack;
        this.evDefense = evDefense;
        this.evSpeed = evSpeed;
        this.evSpAttack = evSpAttack;
        this.evSpDefense = evSpDefense;
    }
    public short getEvYieldByIndex(int index) {
        switch (index) {
            case 0:
                return evHp;
            case 1:
                return evAttack;
            case 2:
                return evDefense;
            case 3:
                return evSpeed;
            case 4:
                return evSpAttack;
            case 5:
                return evSpDefense;
            default:
                return 0;
        }
    }
}
