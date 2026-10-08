package org.pokemmo.gameserver.game.pokemon;

public enum PokemonAppearLoactionType {
    GRASS(0),
    WATER(1),
    ROCK(2),
    //钓鱼
    FISH(3),
    //好钓竿
    GOOD_ROD(4),
    //厉害钓竿
    SUPER_ROD(5),
    //深草丛
    DARK_GRASS(6),
    CAVE(7),
    INSIDE(8),
    //影子
    SHADE(9),
    //卷尘地面
    DUST_CLOUD(10),
    //甜甜蜜树
    HONEY_TREE(11),
    //头锤
    HEADBUTT(12);
    private byte type;
    PokemonAppearLoactionType(int type){
        this.type = (byte)type;
    }
    public byte getType(){
        return type;
    }

}
