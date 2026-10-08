package org.pokemmo.gameserver.game.particleEffectType;

public enum ParticleEffectType {
    NULL(0),
    ANY(1),
    RANDOM(2),
    NONE(3),
    SHINY(4),
    DUMMY(5),
    HITODAMA(6),
    BATS(7),
    GHOST(8),
    EYE(9),
    PUMPKINGS_DELIGHT(10),
    RISING_STAR(11),
    SNOWFLAKES(12),
    PRESENT(13),
    LANTERNS(14),
    FIREWORKS(15),
    PUNGENT_STENCH(16),
    BLACK_HOLE(17),
    WITCHS_HAZE(18),
    PUMPKIDS_TREAT(19),
    BLACK_CAT(20),
    PUMPCAT(21),
    WHITE_CAT(22),
    SPINNING_SCYTHE(23),
    SPIDERS_WEB(24),
    ZOMBIE_GRIP(25),
    ZODIAC(26),
    EERIE_HOWL(27),
    GRAVEYARD(28),
    KINGS_FALL(29),
    RED_DRAGON(30),
    ANNIV_CAKE(31),
    ANNIV_FRIENDS_R0(32),
    ANNIV_FRIENDS_R0_SHINY(33),
    UNKNOWN_34(34),
    UNKNOWN_35(35),
    UNKNOWN_36(36),
    UNKNOWN_37(37),
    UNKNOWN_38(38);
    private byte type;
    private static ParticleEffectType[] allTypeArray = values();
    ParticleEffectType(int type){
        this.type = (byte) type;
    }
    public byte getType(){
        return type;
    }
    public static ParticleEffectType getByType(int type){
        if (type < 0 || type >= allTypeArray.length) {
            return null;
        }
        for(ParticleEffectType particleEffectType : allTypeArray){
            if(particleEffectType.type == type){
                return particleEffectType;
            }
        }
        return null;
    }
}
