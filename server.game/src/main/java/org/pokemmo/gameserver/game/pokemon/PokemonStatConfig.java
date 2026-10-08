package org.pokemmo.gameserver.game.pokemon;

public class PokemonStatConfig {
    private short hp;
    private short attack;
    private short defense;
    private short speed;
    private short sp_attack;
    private short sp_defense;
    public PokemonStat toPokemonStat(){
        return new PokemonStat(hp, attack, defense, speed, sp_attack, sp_defense);
    }
}
