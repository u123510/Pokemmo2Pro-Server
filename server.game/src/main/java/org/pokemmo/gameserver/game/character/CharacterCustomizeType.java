package org.pokemmo.gameserver.game.character;

public enum CharacterCustomizeType {
    NEW_CHARACTER(0),
    RESET_CLOTHES(1),
    MIDDLE_CUSTOMIZED(2),
    SIMPLE_CUSTOMIZED(3),
    SENIOR_CUSTOMIZED(4);
    private byte type;
    CharacterCustomizeType(int type) {
        this.type = (byte) type;
    }
}
