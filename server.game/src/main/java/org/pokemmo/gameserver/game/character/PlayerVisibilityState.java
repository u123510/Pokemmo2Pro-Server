package org.pokemmo.gameserver.game.character;

/** Online-only, character-bound hide state; its monitor serializes hide with outgoing entity packets. */
public final class PlayerVisibilityState {
    private volatile long hiddenCharacterId;

    public boolean isHidden(long characterId) {
        return characterId > 0 && hiddenCharacterId == characterId;
    }

    synchronized boolean toggle(long characterId) {
        if (characterId <= 0) throw new IllegalArgumentException("隐身角色编号无效");
        hiddenCharacterId = isHidden(characterId) ? 0 : characterId;
        return hiddenCharacterId != 0;
    }
}
