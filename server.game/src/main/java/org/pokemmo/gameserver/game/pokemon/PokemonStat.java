package org.pokemmo.gameserver.game.pokemon;

public class PokemonStat {
    private short hp;
    private short attack;
    private short defense;
    private short speed;
    private short sp_attack;
    private short sp_defense;
    public PokemonStat(short hp, short attack, short defense, short speed, short sp_attack, short sp_defense) {
        this.hp = hp;
        this.attack = attack;
        this.defense = defense;
        this.speed = speed;
        this.sp_attack = sp_attack;
        this.sp_defense = sp_defense;
    }
    public short getHpStat() {
        return hp;
    }
    public short getAttackStat() {
        return attack;
    }
    public short getDefenseStat() {
        return defense;
    }
    public short getSpeedStat() {
        return speed;
    }
    public short getSpAttackStat() {
        return sp_attack;
    }
    public short getSpDefenseStat() {
        return sp_defense;
    }
    public short getByStatType(PokemonStatType statType) {
        switch (statType) {
            case HP:
                return hp;
            case ATTACK:
                return attack;
            case DEFENSE:
                return defense;
            case SPEED:
                return speed;
            case SPECIAL_ATTACK:
                return sp_attack;
            case SPECIAL_DEFENSE:
                return sp_defense;
            default:
                return 0;
        }
    }
}
