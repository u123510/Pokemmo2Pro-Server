package org.pokemmo.gameserver.game.battle;

public enum PokemonDisobeyType {
    POKEMON_FALL_DOWN(0),
    ATTACK_SELF(2),
    EXHAUSTED(4);
    private byte type;
    private static PokemonDisobeyType[] allTypeArray= {POKEMON_FALL_DOWN, ATTACK_SELF, EXHAUSTED};
    PokemonDisobeyType(int type){
        this.type = (byte) type;
    }
    public byte getType(){
        return type;
    }
    public static PokemonDisobeyType getTypeByType(byte type){
        for(PokemonDisobeyType disobeyType: allTypeArray){
            if(disobeyType.type == type){
                return disobeyType;
            }
        }
        return null;
    }
}
