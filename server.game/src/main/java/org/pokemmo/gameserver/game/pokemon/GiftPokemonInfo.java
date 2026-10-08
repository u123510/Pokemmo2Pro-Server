package org.pokemmo.gameserver.game.pokemon;

public class GiftPokemonInfo {
    private short giftId;
    private short pokemonIndexId;
    private short level;
    private short[] moves;
    private short item;
    private byte ballType;

    public short getGiftId() {
        return giftId;
    }

    public short getPokemonIndexId() {
        return pokemonIndexId;
    }

    public short getLevel() {
        return level;
    }

    public short[] getMoves() {
        return moves;
    }

    public short getItem() {
        return item;
    }
     public byte getBallType() {
        return ballType;
    }
    public GiftPokemonInfo(short giftId, short pokemonIndexId, short level, short[] moves, short item, byte ballType) {
        this.giftId = giftId;
        this.pokemonIndexId = pokemonIndexId;
        this.level = level;
        this.moves = moves;
        this.item = item;
        this.ballType = ballType;
    }
}
