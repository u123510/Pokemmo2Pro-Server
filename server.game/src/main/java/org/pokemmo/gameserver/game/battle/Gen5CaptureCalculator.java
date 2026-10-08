package org.pokemmo.gameserver.game.battle;

import org.pokemmo.gameserver.game.item.CaptureBallRateManager;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.pokemon.CaptureSpeciesDataManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonEvolution;
import org.pokemmo.gameserver.game.pokemon.PokemonEvolutionConditionType;
import org.pokemmo.gameserver.game.pokemon.PokemonStatusType;
import org.pokemmo.gameserver.game.pokemon.PokemonType;

import java.util.Random;

/** Implements the Generation V wild-capture formula and result animation mapping. */
final class Gen5CaptureCalculator {
    private static final int FIXED_POINT_SCALE = 4096;
    private static final int MAX_CATCH_RATE = 255;
    private static final int MAX_CATCH_RATE_FIXED = MAX_CATCH_RATE * FIXED_POINT_SCALE;
    private static final int RANDOM_BOUND = 65536;
    private static final int TIMER_BALL_INCREMENT = 1229;

    record Environment(BattleFacilityType facility, int battleRound, int highestPlayerLevel,
                       int caughtDexCount, boolean targetAlreadyCaught, boolean fishing,
                       boolean night, int capturePowerPercent) {
        Environment(BattleFacilityType facility, int battleRound, int highestPlayerLevel,
                    int caughtDexCount, boolean targetAlreadyCaught, boolean fishing) {
            this(facility, battleRound, highestPlayerLevel, caughtDexCount,
                    targetAlreadyCaught, fishing, false, 100);
        }

        Environment(BattleFacilityType facility, int battleRound, int highestPlayerLevel,
                    int caughtDexCount, boolean targetAlreadyCaught, boolean fishing,
                    boolean night) {
            this(facility, battleRound, highestPlayerLevel, caughtDexCount,
                    targetAlreadyCaught, fishing, night, 100);
        }

        Environment {
            facility = facility == null ? BattleFacilityType.ROUTE : facility;
            battleRound = Math.max(0, battleRound);
            highestPlayerLevel = Math.max(1, highestPlayerLevel);
            caughtDexCount = Math.max(0, caughtDexCount);
            capturePowerPercent = Math.max(100, Math.min(130, capturePowerPercent));
        }
    }

    record CaptureResult(byte animationResult, boolean caught, int modifiedCatchRate,
                         int shakeThreshold, boolean criticalCapture) {
    }

    CaptureResult calculate(BattlePokemonData user, PokemonData target, ItemData ball,
                            CaptureBallRateManager ballRates, Environment environment,
                            Random random) {
        environment = environment == null
                ? new Environment(BattleFacilityType.ROUTE, 0, 1, 0, false, false)
                : environment;
        random = random == null ? new Random() : random;
        if (target == null || ball == null || !ball.isCaptureBall()) {
            return new CaptureResult((byte) 0, false, 0, 0, false);
        }
        int ballType = Byte.toUnsignedInt(ball.getItemBallType());
        if (ballType == 0) {
            return new CaptureResult((byte) 4, true, MAX_CATCH_RATE_FIXED, RANDOM_BOUND, false);
        }

        CaptureSpeciesDataManager.SpeciesData species =
                CaptureSpeciesDataManager.lookup(Short.toUnsignedInt(target.getPokemonIndexId()));
        double configuredBallMultiplier = ballRates == null
                ? defaultBallMultiplier(ballType)
                : ballRates.getCatchRateMultiplier(ball.getItemIndexId(), ball.getItemBallType());
        int ballMultiplier = conditionalBallMultiplier(
                ballType, user, target, environment, toFixedPoint(configuredBallMultiplier));

        int maxHp = Math.max(1, target.getMaxHp());
        int currentHp = Math.max(1, Math.min(maxHp, target.getCurrentHp()));
        int baseCatchRate = Math.max(1, Math.min(MAX_CATCH_RATE,
                species.catchRate() + heavyBallModifier(ballType, target)));
        int modifiedCatchRate = calculateModifiedCatchRate(
                maxHp, currentHp, baseCatchRate, ballMultiplier,
                darkGrassMultiplier(environment), statusMultiplier(target.getPokemonStatus()),
                environment.capturePowerPercent());

        int shakeThreshold = shakeThreshold(modifiedCatchRate);
        boolean critical = criticalCapture(modifiedCatchRate, environment.caughtDexCount(), random);
        if (modifiedCatchRate >= MAX_CATCH_RATE_FIXED) {
            // The game still performs the critical-capture check, but the result is guaranteed.
            return new CaptureResult((byte) 4, true, modifiedCatchRate, RANDOM_BOUND, critical);
        }
        if (critical) {
            boolean caught = random.nextInt(RANDOM_BOUND) < shakeThreshold;
            // A Generation V critical capture always performs one shake,
            // including the failure animation.
            return new CaptureResult(caught ? (byte) 4 : (byte) 1, caught,
                    modifiedCatchRate, shakeThreshold, true);
        }

        for (int passed = 0; passed < 3; passed++) {
            if (random.nextInt(RANDOM_BOUND) >= shakeThreshold) {
                // Gen V has no normal two-shake failure result.
                byte animation = passed == 0 ? (byte) 0 : passed == 1 ? (byte) 1 : (byte) 3;
                return new CaptureResult(animation, false, modifiedCatchRate,
                        shakeThreshold, false);
            }
        }
        return new CaptureResult((byte) 4, true, modifiedCatchRate, shakeThreshold, false);
    }

    private int conditionalBallMultiplier(int ballType, BattlePokemonData user,
                                          PokemonData target, Environment environment,
                                          int configuredMultiplier) {
        return switch (ballType) {
            case 5 -> hasType(target, PokemonType.BUG) || hasType(target, PokemonType.WATER)
                    ? 3 * FIXED_POINT_SCALE : configuredMultiplier;
            case 6 -> isWaterEncounter(environment)
                    ? 7 * FIXED_POINT_SCALE / 2 : configuredMultiplier;
            case 7 -> nestBallMultiplier(target);
            case 8 -> environment.targetAlreadyCaught()
                    ? 3 * FIXED_POINT_SCALE : configuredMultiplier;
            case 9 -> timerBallMultiplier(environment.battleRound());
            case 12 -> isDarkArea(environment)
                    ? 7 * FIXED_POINT_SCALE / 2 : configuredMultiplier;
            case 14 -> environment.battleRound() == 0
                    ? 5 * FIXED_POINT_SCALE : configuredMultiplier;
            case 16 -> target.getPokemonDexData() != null
                    && target.getPokemonDexData().getPokemonStats().getSpeedStat() >= 100
                    ? 4 * FIXED_POINT_SCALE : configuredMultiplier;
            case 17 -> levelBallMultiplier(user, target, environment.highestPlayerLevel());
            case 18 -> environment.fishing() ? 3 * FIXED_POINT_SCALE : configuredMultiplier;
            case 20 -> user != null && user.getPokemonData() != null
                    && user.getPokemonData().getPokemonDexData() != null
                    && target.getPokemonDexData() != null
                    && user.getPokemonData().getPokemonDexData().getGenderRatio() != 255
                    && target.getPokemonDexData().getGenderRatio() != 255
                    && user.getPokemonData().getPokemonIndexId() == target.getPokemonIndexId()
                    && user.getPokemonSex() != target.getPokemonSex()
                    ? 8 * FIXED_POINT_SCALE : configuredMultiplier;
            case 22 -> canUseMoonStone(target) ? 4 * FIXED_POINT_SCALE : configuredMultiplier;
            default -> configuredMultiplier;
        };
    }

    private double defaultBallMultiplier(int ballType) {
        return switch (ballType) {
            case 1 -> 2.0d;
            case 2 -> 1.5d;
            // Safari Ball (4) and Sport Ball (23) retain the Gen V 1.5x bonus.
            case 4, 23 -> 1.5d;
            default -> 1.0d;
        };
    }

    private int levelBallMultiplier(BattlePokemonData user, PokemonData target, int highestLevel) {
        int playerLevel = user == null || user.getPokemonData() == null
                ? highestLevel : user.getPokemonData().getLevel();
        int targetLevel = Math.max(1, target.getLevel());
        if (playerLevel >= targetLevel * 4) return 8 * FIXED_POINT_SCALE;
        if (playerLevel >= targetLevel * 2) return 4 * FIXED_POINT_SCALE;
        return playerLevel > targetLevel ? 2 * FIXED_POINT_SCALE : FIXED_POINT_SCALE;
    }

    private int heavyBallModifier(int ballType, PokemonData target) {
        if (ballType != 19 || target.getPokemonDexData() == null) return 0;
        int weight = Short.toUnsignedInt(target.getPokemonDexData().getWeight());
        if (weight < 1024) return -20;
        if (weight < 2048) return 0;
        if (weight < 3072) return 20;
        if (weight < 4096) return 30;
        return 40;
    }

    private int statusMultiplier(PokemonStatusType status) {
        if (status == PokemonStatusType.SLEEP || status == PokemonStatusType.FREEZE) {
            return 5 * FIXED_POINT_SCALE / 2;
        }
        if (status == PokemonStatusType.POISON || status == PokemonStatusType.BAD_POISON
                || status == PokemonStatusType.BURN || status == PokemonStatusType.PARALYSIS) {
            return 3 * FIXED_POINT_SCALE / 2;
        }
        return FIXED_POINT_SCALE;
    }

    private int shakeThreshold(int modifiedCatchRate) {
        if (modifiedCatchRate <= 0) return 0;
        double ratio = modifiedCatchRate / (double) MAX_CATCH_RATE_FIXED;
        return Math.min(RANDOM_BOUND, (int) Math.floor(65_536.0d * Math.pow(ratio, 0.25d)));
    }

    private int calculateModifiedCatchRate(int maxHp, int currentHp, int catchRate,
                                           int ballMultiplier, int grassMultiplier,
                                           int statusMultiplier, int capturePowerPercent) {
        long hpNumerator = 3L * maxHp - 2L * currentHp;
        long denominator = 3L * maxHp;
        // Gen V rounds each intermediate value to 1/4096 instead of performing
        // one large floating-point/integer operation at the end:
        // X = down(round(down(round((3M-2H)*G) * C * B) / (3M)) * S * E / 100).
        // Preserve the Gen V round after applying the grass modifier. Without
        // this step, low catch rates can differ by one fixed-point unit.
        long hpGrass = multiplyFixed(hpNumerator * FIXED_POINT_SCALE, grassMultiplier);
        long species = hpGrass * catchRate;
        long ballAdjusted = multiplyFixed(species, ballMultiplier);
        long preStatus = ballAdjusted / denominator;
        long modified = multiplyFixed(preStatus, statusMultiplier);
        // Entralink Capture Power is 100% during ordinary battles and may raise
        // the final value to 110%, 120%, or 130% in Generation V.
        modified = (modified * capturePowerPercent) / 100L;
        return (int) Math.max(0, Math.min((long) MAX_CATCH_RATE_FIXED, modified));
    }

    private long multiplyFixed(long leftFixed, long rightFixed) {
        if (leftFixed <= 0 || rightFixed <= 0) return 0;
        return (leftFixed * rightFixed + FIXED_POINT_SCALE / 2L) / FIXED_POINT_SCALE;
    }

    private int darkGrassMultiplier(Environment environment) {
        if (environment.facility() != BattleFacilityType.GRASS_DARK) {
            return FIXED_POINT_SCALE;
        }
        int caught = environment.caughtDexCount();
        if (caught < 30) return 1229;
        if (caught <= 150) return 2048;
        if (caught <= 300) return 2867;
        if (caught <= 450) return 3277;
        if (caught <= 600) return 3686;
        return FIXED_POINT_SCALE;
    }

    private int nestBallMultiplier(PokemonData target) {
        int level = Math.max(1, target.getLevel());
        // Gen V uses floor((41 - level) / 10), with level 30 still at 1.1x.
        return Math.max(FIXED_POINT_SCALE,
                ((41 - level) * FIXED_POINT_SCALE) / 10);
    }

    private int timerBallMultiplier(int battleRound) {
        long multiplier = FIXED_POINT_SCALE + (long) Math.max(0, battleRound) * TIMER_BALL_INCREMENT;
        return (int) Math.min(4L * FIXED_POINT_SCALE, multiplier);
    }

    private int toFixedPoint(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0d) return 0;
        return (int) Math.min(20L * FIXED_POINT_SCALE,
                Math.round(multiplier * FIXED_POINT_SCALE));
    }

    private boolean criticalCapture(int modifiedCatchRate, int caughtDexCount, Random random) {
        // P is represented as doubled integers (0, 1, 2, 3, 4, 5) so the
        // critical-capture threshold is calculated without floating-point drift:
        // CC = floor(min(255, X) * P / 6).
        int pokedexMultiplierNumerator = caughtDexCount <= 30 ? 0
                : caughtDexCount <= 150 ? 1
                : caughtDexCount <= 300 ? 2
                : caughtDexCount <= 450 ? 3
                : caughtDexCount <= 600 ? 4 : 5;
        long cappedRate = Math.min((long) MAX_CATCH_RATE_FIXED, Math.max(0, modifiedCatchRate));
        int criticalValue = (int) (cappedRate * pokedexMultiplierNumerator
                / (12L * FIXED_POINT_SCALE));
        return criticalValue > 0 && random.nextInt(256) < criticalValue;
    }

    private boolean isWaterEncounter(Environment environment) {
        return environment.fishing()
                || environment.facility() == BattleFacilityType.UNDERWATER
                || environment.facility() == BattleFacilityType.WATER
                || environment.facility() == BattleFacilityType.WATER_CALM;
    }

    private boolean isDarkArea(Environment environment) {
        return environment.facility() == BattleFacilityType.CAVE
                || (environment.night() && environment.facility() != BattleFacilityType.INSIDE);
    }

    private boolean hasType(PokemonData target, PokemonType type) {
        return target != null && target.getPokemonDexData() != null
                && (target.getPokemonFirstType() == type || target.getPokemonSecondType() == type);
    }

    private boolean canUseMoonStone(PokemonData target) {
        if (target.getPokemonDexData() == null || target.getPokemonDexData().getPokemonEvolutions() == null) {
            return false;
        }
        for (PokemonEvolution evolution : target.getPokemonDexData().getPokemonEvolutions()) {
            if (evolution.getVal() == 5081
                    && (evolution.getConditionType() == PokemonEvolutionConditionType.ITEM
                    || evolution.getConditionType() == PokemonEvolutionConditionType.ITEM_MALE
                    || evolution.getConditionType() == PokemonEvolutionConditionType.ITEM_FEMALE)) {
                return true;
            }
        }
        return false;
    }
}
