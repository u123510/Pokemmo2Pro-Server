package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatType;
import org.pokemmo.gameserver.game.pokemon.PokemonType;

/**
 * Generation V style damage calculation shared by physical and special moves.
 * Stat values already include the battle stat calculator's nature, item,
 * ability and stat-stage rules; this class applies move-level modifiers.
 */
final class BattleDamageCalculator {
    private final BattleContextState context;

    BattleDamageCalculator(BattleContextState context) {
        this.context = context;
    }

    DamageResult calculate(PokemonMoveData move, MoveDamageType damageType,
                           BattlePokemonData attacker, BattlePokemonData target,
                           int targetCount) {
        if (move == null || attacker == null || target == null
                || attacker.getPokemonData() == null || target.getPokemonData() == null
                || damageType == null || damageType == MoveDamageType.STATUS) {
            return DamageResult.zero();
        }
        PokemonType moveType = move.getMovePokemonType();
        double typeEffectiveness = calculateTypeEffectiveness(moveType, attacker, target);
        Integer fixedDamage = calculateFixedDamage(move, attacker, target);
        if (fixedDamage != null) {
            if (typeEffectiveness == 0.0d) {
                target.updateSufferFlag(PokemonSufferType.NO_EFFECT.getType());
                return new DamageResult(0, false, typeEffectiveness);
            }
            int damage = Math.max(0, Math.min(target.getPokemonData().getCurrentHp(), fixedDamage));
            target.updateSufferFlag(PokemonSufferType.NO_DAMAGE.getType());
            return new DamageResult(damage, false, 1.0d);
        }
        if (move.getMoveBasePower() <= 0) {
            return DamageResult.zero();
        }
        if (typeEffectiveness == 0.0d) {
            target.updateSufferFlag(PokemonSufferType.NO_EFFECT.getType());
            return new DamageResult(0, false, typeEffectiveness);
        }

        int level = clamp(attacker.getPokemonData().getLevel(), 1, 100);
        int power = Math.max(1, move.getMoveBasePower());
        int attack = damageType == MoveDamageType.PHYSICAL
                ? context.getPokemonAttackStat(attacker)
                : context.getPokemonSpAttackStat(attacker);
        int defense = damageType == MoveDamageType.PHYSICAL
                ? context.getPokemonDefenseStat(target)
                : context.getPokemonSpDefenseStat(target);
        if (attack <= 0 || defense <= 0) {
            return new DamageResult(0, false, typeEffectiveness);
        }

        boolean criticalHit = rollCriticalHit(attacker, target);
        attack = ignoreStageForCritical(attack, attacker, damageType, true, criticalHit, false);
        defense = ignoreStageForCritical(defense, target, damageType, false,
                criticalHit, false);
        int baseDamage = (int) Math.min(Integer.MAX_VALUE, floorDivide(
                floorDivide(floorDivide((2L * level) + 10L, 5L) * power * attack, defense), 50L) + 2L);
        int damage = applyMultiplier(baseDamage, targetCount > 1 ? 0.75d : 1.0d);
        damage = applyMultiplier(damage, weatherMultiplier(moveType));
        damage = applyMultiplier(damage, criticalHit ? criticalMultiplier(attacker) : 1.0d);
        damage = applyMultiplier(damage, 0.85d + context.random.nextInt(16) / 100.0d);
        damage = applyMultiplier(damage, stabMultiplier(moveType, attacker));
        damage = applyMultiplier(damage, typeEffectiveness);
        if (damage <= 0 && typeEffectiveness > 0.0d) {
            damage = 1;
        }
        damage = applyMultiplier(damage, defensiveAbilityMultiplier(moveType, target, typeEffectiveness, attacker));
        target.updateSufferFlag((short) (effectivenessFlag(typeEffectiveness)
                | (criticalHit ? PokemonSufferType.CRIT.getType() : 0)));
        return new DamageResult(Math.min(target.getPokemonData().getCurrentHp(), damage), criticalHit, typeEffectiveness);
    }

    private double calculateTypeEffectiveness(PokemonType moveType, BattlePokemonData attacker,
                                              BattlePokemonData target) {
        if (moveType == null || moveType == PokemonType.NONE || moveType == PokemonType.QUESTION) {
            return 1.0d;
        }
        boolean moldBreaker = attacker.getPokemonAbilityIndexId() == 104;
        if (!moldBreaker && (target.getPokemonAbilityIndexId() == 10 && moveType == PokemonType.ELECTRIC
                || target.getPokemonAbilityIndexId() == 11 && moveType == PokemonType.WATER
                || target.getPokemonAbilityIndexId() == 18 && moveType == PokemonType.FIRE
                || target.getPokemonAbilityIndexId() == 31 && moveType == PokemonType.ELECTRIC
                || target.getPokemonAbilityIndexId() == 26 && moveType == PokemonType.GROUND
                && !target.isOnGround()
                || target.getPokemonAbilityIndexId() == 114 && moveType == PokemonType.WATER
                || target.getPokemonAbilityIndexId() == 87 && moveType == PokemonType.WATER
                || target.getPokemonAbilityIndexId() == 157 && moveType == PokemonType.GRASS)) {
            return 0.0d;
        }
        double first = typeAgainst(moveType, attacker, target, target.getPokemonFirstType());
        double result = first;
        PokemonType secondType = target.getPokemonSecondType();
        if (secondType != target.getPokemonFirstType()) {
            result *= typeAgainst(moveType, attacker, target, secondType);
        }
        if (!moldBreaker && target.getPokemonAbilityIndexId() == 25 && result <= 1.0d) {
            return 0.0d;
        }
        return result;
    }

    private double typeAgainst(PokemonType moveType, BattlePokemonData attacker,
                               BattlePokemonData target, PokemonType targetType) {
        if (targetType == null || targetType == PokemonType.NONE || targetType == PokemonType.QUESTION) {
            return 1.0d;
        }
        if (moveType == PokemonType.GROUND && targetType == PokemonType.FLYING && target.isOnGround()) {
            return 1.0d;
        }
        if (attacker.getPokemonAbilityIndexId() == 113
                && targetType == PokemonType.GHOST
                && (moveType == PokemonType.NORMAL || moveType == PokemonType.FIGHTING)) {
            return 1.0d;
        }
        if (target.isCanHurtByNormalAndFightType()
                && targetType == PokemonType.GHOST
                && (moveType == PokemonType.NORMAL || moveType == PokemonType.FIGHTING)) {
            return 1.0d;
        }
        if (target.isCanHurtByPsychicType()
                && targetType == PokemonType.DARK
                && moveType == PokemonType.PSYCHIC) {
            return 1.0d;
        }
        return moveType.getEffectivenessRate(moveType, targetType);
    }

    private short effectivenessFlag(double effectiveness) {
        if (effectiveness == 0.0d) {
            return PokemonSufferType.NO_EFFECT.getType();
        } else if (effectiveness < 1.0d) {
            return PokemonSufferType.NOT_VERY_EFFECT.getType();
        } else if (effectiveness > 1.0d) {
            return PokemonSufferType.SUPER_EFFECTIVE.getType();
        }
        return PokemonSufferType.NO_DAMAGE.getType();
    }

    private boolean rollCriticalHit(BattlePokemonData attacker, BattlePokemonData target) {
        if (target.getPokemonAbilityIndexId() == 4 || target.getPokemonAbilityIndexId() == 75) {
            return false;
        }
        return switch (attacker.getCriticalHitLevel()) {
            case 1 -> context.random.nextDouble() <= 0.042d;
            case 2 -> context.random.nextDouble() <= 0.5d;
            case 3 -> true;
            default -> false;
        };
    }

    private double criticalMultiplier(BattlePokemonData attacker) {
        return attacker.getPokemonAbilityIndexId() == 97 ? 2.25d : 1.5d;
    }

    private double stabMultiplier(PokemonType moveType, BattlePokemonData attacker) {
        if (moveType == null || moveType == PokemonType.NONE || moveType == PokemonType.QUESTION
                || (moveType != attacker.getPokemonFirstType() && moveType != attacker.getPokemonSecondType())) {
            return 1.0d;
        }
        return attacker.getPokemonAbilityIndexId() == 91 ? 2.0d : 1.5d;
    }

    private double weatherMultiplier(PokemonType moveType) {
        if (moveType == null || !context.battleBasisInfo.getBattlePublicFieldInfo().isHasWeather()
                || context.battleBasisInfo.getBattlePublicFieldInfo().getWeatherRemainRound() <= 0
                || weatherSuppressed()) {
            return 1.0d;
        }
        BattleWeatherType weather = context.battleBasisInfo.getBattlePublicFieldInfo().getBattleWeatherType();
        if (weather == BattleWeatherType.RAIN) {
            return moveType == PokemonType.WATER ? 1.5d : moveType == PokemonType.FIRE ? 0.5d : 1.0d;
        }
        if (weather == BattleWeatherType.SUNNY) {
            return moveType == PokemonType.FIRE ? 1.5d : moveType == PokemonType.WATER ? 0.5d : 1.0d;
        }
        return 1.0d;
    }

    private boolean weatherSuppressed() {
        for (FactionData faction : context.debutFactions) {
            for (BattlePokemonData pokemon : faction.getDebutPokemons()) {
                if (pokemon != null && pokemon.getPokemonData() != null
                        && pokemon.getPokemonData().getCurrentHp() > 0
                        && (pokemon.getPokemonAbilityIndexId() == 13
                        || pokemon.getPokemonAbilityIndexId() == 76)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double defensiveAbilityMultiplier(PokemonType moveType, BattlePokemonData target,
                                              double typeEffectiveness, BattlePokemonData attacker) {
        if (attacker.getPokemonAbilityIndexId() == 104) {
            return 1.0d;
        }
        if (target.getPokemonAbilityIndexId() == 47
                && (moveType == PokemonType.FIRE || moveType == PokemonType.ICE)) {
            return 0.5d;
        }
        if (target.getPokemonAbilityIndexId() == 87 && moveType == PokemonType.FIRE) {
            return 1.25d;
        }
        if ((target.getPokemonAbilityIndexId() == 111 || target.getPokemonAbilityIndexId() == 116)
                && typeEffectiveness > 1.0d) {
            return 0.75d;
        }
        if (target.getPokemonAbilityIndexId() == 136
                && target.getPokemonData().getCurrentHp() == target.getPokemonData().getMaxHp()) {
            return 0.5d;
        }
        return 1.0d;
    }

    private int ignoreStageForCritical(int stat, BattlePokemonData pokemon,
                                       MoveDamageType damageType, boolean attacker,
                                       boolean ignoreStage, boolean ignoreAllStages) {
        if (!ignoreStage && !ignoreAllStages) {
            return stat;
        }
        PokemonStatType statType = damageType == MoveDamageType.PHYSICAL
                ? (attacker ? PokemonStatType.ATTACK : PokemonStatType.DEFENSE)
                : (attacker ? PokemonStatType.SPECIAL_ATTACK : PokemonStatType.SPECIAL_DEFENSE);
        byte stage = pokemon.getStaticStats()[statType.getType()];
        if (stage == 0) {
            return stat;
        }
        if (ignoreAllStages || (ignoreStage && (attacker && stage < 0 || !attacker && stage > 0))) {
            double stageRate = stageMultiplier(stage);
            return Math.max(1, (int) Math.floor(stat / stageRate));
        }
        return stat;
    }

    private double stageMultiplier(byte stage) {
        return stage > 0 ? (2.0d + stage) / 2.0d : 2.0d / (2.0d + Math.abs(stage));
    }

    private Integer calculateFixedDamage(PokemonMoveData move, BattlePokemonData attacker, BattlePokemonData target) {
        if (!move.isTrueDamage()) {
            return null;
        }
        int level = Math.max(1, attacker.getPokemonData().getLevel());
        int previousDamage = attacker.getLastDamageTaken();
        return switch (move.getMoveIndexId()) {
            case 12, 32, 90, 329 -> level >= target.getPokemonData().getLevel()
                    ? Integer.valueOf(target.getPokemonData().getCurrentHp()) : 0;
            case 49 -> 20;
            case 68 -> attacker.getLastDamageType() == MoveDamageType.PHYSICAL ? previousDamage * 2 : 0;
            case 69, 101 -> level;
            case 82 -> 40;
            case 149 -> Math.max(1, (int) Math.floor(level * (0.5d + context.random.nextDouble())));
            case 162 -> Math.max(1, target.getPokemonData().getCurrentHp() / 2);
            case 243 -> attacker.getLastDamageType() == MoveDamageType.SPECIAL ? previousDamage * 2 : 0;
            case 283 -> Math.max(0, target.getPokemonData().getCurrentHp()
                    - attacker.getPokemonData().getCurrentHp());
            case 368 -> (int) Math.floor(previousDamage * 1.5d);
            case 515 -> Integer.valueOf(attacker.getPokemonData().getCurrentHp());
            default -> null;
        };
    }

    private int applyMultiplier(int value, double multiplier) {
        return (int) Math.ceil(value * multiplier - 0.5d);
    }

    private long floorDivide(long numerator, long denominator) {
        return denominator <= 0 ? 0 : numerator / denominator;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record DamageResult(int damage, boolean criticalHit, double typeEffectiveness) {
        static DamageResult zero() {
            return new DamageResult(0, false, 1.0d);
        }
    }
}
