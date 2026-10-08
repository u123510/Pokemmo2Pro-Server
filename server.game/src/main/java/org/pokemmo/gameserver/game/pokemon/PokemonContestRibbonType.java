package org.pokemmo.gameserver.game.pokemon;

public enum PokemonContestRibbonType {
    COOL_RIBBON(0, 0, 1),
    COOL_RIBBON_SUPER(1, 0, 2),
    COOL_RIBBON_HYPER(2, 0, 3),
    COOL_RIBBON_MASTER(3, 0, 4),
    BEAUTY_RIBBON(4,1,1),
    BEAUTY_RIBBON_SUPER(5,1,2),
    BEAUTY_RIBBON_HYPER(6,1,3),
    BEAUTY_RIBBON_MASTER(7,1,4),
    CUTE_RIBBON(8,2,1),
    CUTE_RIBBON_SUPER(9,2,2),
    CUTE_RIBBON_HYPER(10,2,3),
    CUTE_RIBBON_MASTER(11,2,4),
    SMART_RIBBON(12,3,1),
    SMART_RIBBON_SUPER(13,3,2),
    SMART_RIBBON_HYPER(14,3,3),
    SMART_RIBBON_MASTER(15,3,4),
    TOUGH_RIBBON(16,4,1),
    TOUGH_RIBBON_SUPER(17,4,2),
    TOUGH_RIBBON_HYPER(18,4,3),
    TOUGH_RIBBON_MASTER(19,4,4);
    private byte index;
    private byte ribbonGroup;
    private byte level;
    static PokemonContestRibbonType[] allTypeArray = {
                COOL_RIBBON,
                COOL_RIBBON_SUPER,
                COOL_RIBBON_HYPER,
                COOL_RIBBON_MASTER,
                BEAUTY_RIBBON,
                BEAUTY_RIBBON_SUPER,
                BEAUTY_RIBBON_HYPER,
                BEAUTY_RIBBON_MASTER,
                CUTE_RIBBON,
                CUTE_RIBBON_SUPER,
                CUTE_RIBBON_HYPER,
                CUTE_RIBBON_MASTER,
                SMART_RIBBON,
                SMART_RIBBON_SUPER,
                SMART_RIBBON_HYPER,
                SMART_RIBBON_MASTER,
                TOUGH_RIBBON,
                TOUGH_RIBBON_SUPER,
                TOUGH_RIBBON_HYPER,
                TOUGH_RIBBON_MASTER
    };
    PokemonContestRibbonType(int index, int ribbonGroup, int level) {
        this.index = (byte) index;
        this.ribbonGroup = (byte) ribbonGroup;
        this.level = (byte) level;
    }
    public byte getIndex() {
        return index;
    }
    public byte getRibbonGroup() {
        return ribbonGroup;
    }
    public byte getLevel() {
        return level;
    }
    public static PokemonContestRibbonType getByIndex(int index) {
        return allTypeArray[index];
    }
}
