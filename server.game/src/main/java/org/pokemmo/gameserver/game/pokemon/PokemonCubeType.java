package org.pokemmo.gameserver.game.pokemon;

public enum PokemonCubeType {
    RED_POKEMON_CUBE(0),
    BLUE_POKEMON_CUBE(1),
    PINK_POKEMON_CUBE(2),
    GREEN_POKEMON_CUBE(3),
    YELLOW_POKEMON_CUBE(4),
    PURPLE_POKEMON_CUBE(5),
    DARK_BLUE_POKEMON_CUBE(6),
    //茶色
    TAWNY_POKEMON_CUBE(7),
    CYAN_POKEMON_CUBE(8),
    YELLOW_GREEN_POKEMON_CUBE(9),
    GRAY_POKEMON_CUBE(10),
    BLACK_POKEMON_CUBE(11),
    WHITE_POKEMON_CUBE(12),
    GOLD_POKEMON_CUBE(13);
    private byte type;

    PokemonCubeType(int type) {
        this.type = (byte) type;
    }
}
