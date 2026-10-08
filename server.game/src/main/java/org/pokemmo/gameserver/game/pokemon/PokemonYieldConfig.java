package org.pokemmo.gameserver.game.pokemon;

public class PokemonYieldConfig {
    private short exp;
    private short ev_hp;
    private short ev_attack;
    private short ev_defense;
    private short ev_speed;
    private short ev_sp_attack;
    private short ev_sp_defense;
    public PokemonYield toPokemonYield(){
        return new PokemonYield(exp, ev_hp, ev_attack, ev_defense, ev_speed, ev_sp_attack, ev_sp_defense);
    }
}
