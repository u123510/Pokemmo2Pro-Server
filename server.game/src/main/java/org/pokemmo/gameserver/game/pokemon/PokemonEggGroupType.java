package org.pokemmo.gameserver.game.pokemon;

public enum PokemonEggGroupType {
    NONE(0),
    MONSTER(1),
    WATER_1(2),
    BUG(3),
    FLY(4),
    FIELD(5),
    //妖精
    FAIRY(6),
    GRASS(7),
    HUMAN_LIKE(8),
    WATER_3(9),
    MINERAL(10),
    //不定形
    AMORPHOUS(11),
    WATER_2(12),
    DITTO(13),
    DRAGON(14),
    UNKNOWN(15),
    NOSEX(16);
    private byte index;
    PokemonEggGroupType(int index){
        this.index = (byte)index;
    }
    public static final PokemonEggGroupType[] allTypeArray = values();
    public byte getIndex(){
        return index;
    }
    public static PokemonEggGroupType getByType(byte type){
        return allTypeArray[type];
    }
}
