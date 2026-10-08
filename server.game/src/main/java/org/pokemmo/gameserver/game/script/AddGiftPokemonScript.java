package org.pokemmo.gameserver.game.script;

public class AddGiftPokemonScript extends GameScript {
    private short giftId;
    public AddGiftPokemonScript(int giftId) {
        super(ScriptActionType.ADD_GIFT_POKEAMON);
        this.giftId = (short) giftId;
    }
    public short getGiftId() {
        return this.giftId;
    }
}
