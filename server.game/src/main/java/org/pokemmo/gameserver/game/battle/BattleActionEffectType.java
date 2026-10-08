package org.pokemmo.gameserver.game.battle;

public enum BattleActionEffectType {
    MULTI_STAT_CHANGE(-14),//一次性多个属性改变，冥想
    SHOW_POKEMON_ITEM(-12),//展示宝可梦的道具
    FORCE_EXCHANGE(-11),//宝可梦强制交换，吼叫，龙尾
    DAMAGE(0),
    STAT_CHANGE(1),
    STATUS_AILMENT(2),
    BURN(3),
    FLINCH(4),
    FROZEN(5),
    POISON(6),
    SLEEP(7),
    PARALYZE(8),
    CONFUSE(10),
    DISOBEY(11),
    WEATHER(12),
    WEATHER_DAMAGE(13),
    LEECH_SEED(14),
    PAYDAY(15),
    MAGNITUDE(16),
    FACTION_STAT_CHANGE(17),
    CURSE(18),
    NIGHTMARE(19),
    RAGE(20),
    SNATCH(21),
    ENDURE(22),
    THIEF(23),
    BIND(24),
    SPIKES(25),
    SPIKES_DAMAGE(26),
    LEECH_SEEDED(27),
    SAFEGUARD(28),
    SPORT(29),
    SPITE(30),
    TRANSFORM(31),
    ATTRACT(32),
    SKETCH(33),
    YAWN(34),
    TAUNTED(35),
    TAUNTED_SKILL_FAILED(36),
    LOCKED_IN_BATTLE(37),//黑色目光等，紧张逃跑
    INGRAIN(38),
    INGRAIN_HEAL(39),
    WISH_START(40),
    WISH(41),
    FUTURE_SIGHT_START(42),
    FUTURE_SIGHT_LAND(43),
    DESTINY_BOND(44),
    TORMENT(45),
    DISABLE(46),
    ENCORE(47),
    UPROAR(48),
    GRUDGE(49),
    MAGIC_COAT(50),
    ABILITY_TRIGGER(51),
    BERRY_CURE_NON_VOLATILE_AILMENT(52),
    BERRY_CURE_CONFUSE(53),
    BERRY_RESTORE_PP(54),
    BERRY_RESTORE_HP(55),
    BERRY_STAT_CHANGE(56),
    BERRY_INCREASE_CRIT_RATE(57),//聚气
    SUBSTITUTE(58),
    BIDE(59),
    SEALING_MOVES(60),
    SEALED_MOVE(61),
    ELEMENTAL_TYPE_CHANGE(62),
    HELPING_HAND(63),
    NATURE_POWER(64),
    ABILITY_SWAP(65),
    ABILITY_COPY(66),
    FOLLOW_ME(67),
    TRICK(68),
    MIMIC(69),
    RECYCLE(70),
    KNOCKOFF(71),
    SPLASH(72),//溅起水花，什么也没发生
    ELEMENTAL_TYPE_CHANGE_ABILITY(73),//改变宝可梦的技能效果，识破，气味侦测，奇迹之眼
    BATTLE_CLAUSE(74),//触发条款，不能用
    SKILL_LOCK(75),
    BATTLE_MESSAGE(76),
    PERISH_COUNT(77),//灭亡之歌剩余回合数
    STOCK_PILE(78),
    FORM_CHANGE(79),
    NATURE_BATTLE(80),
    OHKO_JUDGE(81),
    HELD_ITEM_CURE_STAT_STAGE(82),
    HELD_ITEM_RESTORE_HP_LITTLE(83),
    REVIVE(84),
    HALLOWEEN_EVENT(85),
    BOSS_EFFECT(86),
    HELD_ITEM_ALLOW_SWAP_OUT(87),
    HELD_ITEM_DAMAGE_BOOST_HURT_SELF(88),
    ITEM_INCREASE_MOVE_DAMAGE(91),//道具提升技能威力，如属性宝石
    CONTEST_ACTION(100),
    CONTEST_RESULTS(101),
    SHOW_ITEM(118),
    FORE_WARN(119);
    private byte type;
    private static BattleActionEffectType[] allTypeArray = values();
    public static BattleActionEffectType getType(byte type){
        try{
            for (BattleActionEffectType actionEffectType : allTypeArray) {
                if (actionEffectType.type == type) {
                    return actionEffectType;
                }
            }
            throw new RuntimeException("未知的宝可梦行动结果类型: " + type);
        }catch (ArrayIndexOutOfBoundsException e){
            throw new RuntimeException("未知的宝可梦行动结果类型: " + type);
        }
    }
    BattleActionEffectType(int type) {
        this.type = (byte) type;
    }
    public byte getType(){
        return type;
    }
}
