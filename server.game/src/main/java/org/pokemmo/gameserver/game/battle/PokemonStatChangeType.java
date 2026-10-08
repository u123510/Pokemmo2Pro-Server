package org.pokemmo.gameserver.game.battle;

public enum PokemonStatChangeType {
    NORMAL_CHANGE(0), //普通变化
    SHRUGGED_DROP(1), //摆脱下降
    INCREASE_DISSIPATED(2); //增幅结束
    private byte type;
    PokemonStatChangeType(int type){
        this.type = (byte) type;
    }
    public byte getType(){
        return type;
    }
}
