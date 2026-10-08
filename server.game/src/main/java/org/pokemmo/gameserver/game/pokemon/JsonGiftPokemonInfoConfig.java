package org.pokemmo.gameserver.game.pokemon;

public class JsonGiftPokemonInfoConfig {
    private short giftId;
    private short pokemonIndexId;
    private short level;
    private short[] moves;
    private short item;
    private byte ballType;
    public GiftPokemonInfo toGiftPokemonInfo() {
        return new GiftPokemonInfo(giftId, pokemonIndexId, level, moves, item, ballType);
    }
    public short getGiftId()  { return giftId; }
}
