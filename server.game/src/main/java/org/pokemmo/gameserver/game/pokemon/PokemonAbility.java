package org.pokemmo.gameserver.game.pokemon;

public class PokemonAbility {
    private short abilityIndexId;
    public PokemonAbility(int abilityIndexId) {
        this.abilityIndexId = (short) abilityIndexId;
    }
    public short getAbilityIndexId() {
        return abilityIndexId;
    }
}
