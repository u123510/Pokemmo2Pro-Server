package org.pokemmo.gameserver.game.trainer;

public class JsonTrainerPokemonConfig {
    private short pokemonIndexId;
    private short containerPos;
    private short level;
    private int personalityValue;
    private short[] pokemonIvs;
    private short[] pokemonEvs;
    private byte ability;
    private short[] moves;
    private short[] movesPp;
    private short item;
    private byte ballType;
    public TrainerPokemonData toTrainerPokemonData() {
        return new TrainerPokemonData(pokemonIndexId, containerPos, level, personalityValue, pokemonIvs, pokemonEvs, ability, moves, movesPp, item, ballType);
    }
}
