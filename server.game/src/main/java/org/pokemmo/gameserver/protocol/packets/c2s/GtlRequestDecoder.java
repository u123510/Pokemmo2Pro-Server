package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.gtl.GtlFilterType;
import org.pokemmo.gameserver.game.gtl.GtlGenderFilterType;
import org.pokemmo.gameserver.game.gtl.GtlRequestState;
import org.pokemmo.gameserver.game.gtl.GtlRuleType;
import org.pokemmo.gameserver.game.gtl.GtlShinyFilterType;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonEggGroupType;
import org.pokemmo.gameserver.game.pokemon.PokemonNatureType;
import org.pokemmo.gameserver.game.skin.SkinType;
import org.server.bytes.ByteBufEx;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Decodes and validates GTL wire rules independently from packet handling. */
final class GtlRequestDecoder {
    GtlDecodedRequest decode(ByteBufEx buffer) {
        byte requestSequence = buffer.readByte();
        byte listType = buffer.readByte();
        GtlFilterType filterType = GtlFilterType.getByType(buffer.readUnsignedByte());
        short pageIndex = buffer.readShortLE();
        int ruleSize = buffer.readUnsignedByte();
        if (filterType == null || pageIndex < 0) {
            throw new IllegalArgumentException("非法的交易行请求头");
        }

        Values values = new Values();
        List<Short> listingIndexIds = new ArrayList<>();
        for (int i = 0; i < ruleSize; i++) {
            GtlRuleType ruleType = GtlRuleType.getByType(buffer.readUnsignedByte());
            if (ruleType == null) {
                throw new IllegalArgumentException("不支持的交易行筛选规则类型");
            }
            decodeRule(buffer, ruleType, values, listingIndexIds);
        }
        validateRanges(values);
        int normalizedListType = listType & 0xFF;
        List<Short> pokemonDexIds = normalizedListType == 1 ? List.of() : List.copyOf(listingIndexIds);
        List<Short> itemIndexIds = normalizedListType == 1 ? List.copyOf(listingIndexIds) : List.of();
        return new GtlDecodedRequest(requestSequence, listType, filterType,
                values.genderFilterType, values.minPrice, values.maxPrice,
                values.minLevel, values.maxLevel, values.minEggYearTimestamp,
                values.maxEggYearTimestamp, values.eggGroupFilterType,
                values.pokemonNatureType, values.shinyFilterType,
                values.particleFilterType, values.learnedMoveIndexId,
                values.pokemonAbilityIndexId, values.hiddenAbilityFilterType,
                values.alphaFilterType, values.minIvLimitIndex, values.minIvLimit,
                values.maxIvLimitIndex, values.maxIvLimit, values.minEvLimitIndex,
                values.minEvLimit, values.maxEvLimitIndex, values.maxEvLimit,
                values.ivMinMatchCount, values.hideFemaleOnlySpecies,
                values.hideDitto, values.altFormUnlocked, values.itemFashionSlot,
                pageIndex, pokemonDexIds, itemIndexIds);
    }

    private void decodeRule(ByteBufEx buffer, GtlRuleType ruleType, Values values,
                            List<Short> listingIndexIds) {
        switch (ruleType) {
            case POKEMON_INDEX_ID -> {
                int count = buffer.readUnsignedShortLE();
                if (count == 0 || buffer.readableBytes() < count * Short.BYTES) {
                    throw new IllegalArgumentException("非法的交易行索引规则长度");
                }
                for (int i = 0; i < count; i++) {
                    int id = buffer.readUnsignedShortLE();
                    if (id == 0 || id > Short.MAX_VALUE) {
                        throw new IllegalArgumentException("非法的交易行索引ID");
                    }
                    listingIndexIds.add((short) id);
                }
            }
            case POKEMON_SEX -> {
                values.genderFilterType = GtlGenderFilterType.getByType(buffer.readUnsignedByte());
                if (values.genderFilterType == null) throw new IllegalArgumentException("非法的宝可梦性别限制");
            }
            case POKEMON_NATURE -> {
                int value = buffer.readUnsignedByte();
                if (value >= PokemonNatureType.values().length) throw new IllegalArgumentException("非法的宝可梦性格限制");
                values.pokemonNatureType = PokemonNatureType.getByType(value);
            }
            case MIN_LEVEL -> values.minLevel = readLevel(buffer);
            case MAX_LEVEL -> values.maxLevel = readLevel(buffer);
            case MIN_IV -> { values.minIvLimitIndex = readIvIndex(buffer); values.minIvLimit = readIvValue(buffer); }
            case MAX_IV -> { values.maxIvLimitIndex = readIvIndex(buffer); values.maxIvLimit = readIvValue(buffer); }
            case EGG_GROUP -> {
                int value = buffer.readUnsignedByte();
                if (value < 1 || value >= PokemonEggGroupType.values().length) throw new IllegalArgumentException("非法的宝可梦蛋组限制");
                values.eggGroupFilterType = PokemonEggGroupType.getByType((byte) value);
            }
            case SHINY -> {
                values.shinyFilterType = GtlShinyFilterType.getByType(buffer.readUnsignedByte());
                if (values.shinyFilterType == null) throw new IllegalArgumentException("非法的宝可梦闪光筛选类型");
            }
            case MIN_PRICE -> values.minPrice = readNonNegativeInt(buffer, "最小价格");
            case MAX_PRICE -> values.maxPrice = readNonNegativeInt(buffer, "最大价格");
            case PVP_LEVEL, ITEM_POCKET -> buffer.readByte();
            case POKEMON_PARTICLE -> {
                int value = buffer.readUnsignedByte();
                if (value != GtlRequestState.ANY_PARTICLE_FILTER_TYPE
                        && (value > 38 || ParticleEffectType.getByType(value) == null)) {
                    throw new IllegalArgumentException("非法的宝可梦粒子限制类型");
                }
                values.particleFilterType = value;
            }
            case POKEMON_HIDDEN_ABILITY -> {
                int value = buffer.readUnsignedByte();
                if (value != GtlRequestState.HIDDEN_ABILITY_FALSE && value != GtlRequestState.HIDDEN_ABILITY_TRUE) throw new IllegalArgumentException("非法的隐藏特性限制");
                values.hiddenAbilityFilterType = value;
            }
            case POKEMON_ALPHA -> {
                int value = buffer.readUnsignedByte();
                if (value < GtlRequestState.ALPHA_ONLY || value > GtlRequestState.FATEFUL_ENCOUNTER) throw new IllegalArgumentException("非法的头目类型限制");
                values.alphaFilterType = value;
            }
            case EGG_YEAR -> {
                long min = Integer.toUnsignedLong(buffer.readIntLE());
                long max = Integer.toUnsignedLong(buffer.readIntLE());
                if (min > max) throw new IllegalArgumentException("非法的蛋年时间范围");
                values.minEggYearTimestamp = min;
                values.maxEggYearTimestamp = max;
                Instant.ofEpochSecond(min).atZone(ZoneOffset.UTC).getYear();
            }
            case LEARNED_MOVE -> {
                int value = buffer.readUnsignedShortLE();
                if (value == 0 || value > Short.MAX_VALUE) throw new IllegalArgumentException("非法的已学习技能索引ID");
                values.learnedMoveIndexId = value;
            }
            case MIN_EV -> { values.minEvLimitIndex = readEvIndex(buffer); values.minEvLimit = readEvValue(buffer); }
            case MAX_EV -> { values.maxEvLimitIndex = readEvIndex(buffer); values.maxEvLimit = readEvValue(buffer); }
            case IV_MIN_MATCH -> {
                int value = buffer.readUnsignedShortLE();
                if (value > GtlRequestState.MAX_IV_MIN_MATCH_COUNT) throw new IllegalArgumentException("非法的个体值匹配数量");
                values.ivMinMatchCount = value;
            }
            case HIDDEN_ONLY_FEMALE -> values.hideFemaleOnlySpecies = true;
            case HIDDEN_DITTO -> values.hideDitto = true;
            case ALT_FORM_UNLOCKED -> values.altFormUnlocked = true;
            case POKEMON_ABILITY -> {
                int value = buffer.readUnsignedShortLE();
                if (value == 0 || value > Short.MAX_VALUE) throw new IllegalArgumentException("非法的宝可梦特性索引");
                values.pokemonAbilityIndexId = value;
            }
            case ITEM_FASHION_SLOT -> {
                int value = buffer.readUnsignedByte();
                if (!SkinType.isGtlFashionSlot(value)) throw new IllegalArgumentException("非法的时装部位限制索引");
                values.itemFashionSlot = value;
            }
        }
    }

    private static void validateRanges(Values values) {
        if (values.minPrice != null && values.maxPrice != null && values.minPrice > values.maxPrice) throw new IllegalArgumentException("最小价格不能高于最大价格");
        if (values.minLevel != null && values.maxLevel != null && values.minLevel > values.maxLevel) throw new IllegalArgumentException("最低等级不能高于最高等级");
        if (values.minIvLimitIndex != null && values.minIvLimitIndex.equals(values.maxIvLimitIndex)
                && values.minIvLimit != null && values.maxIvLimit != null && values.minIvLimit > values.maxIvLimit) throw new IllegalArgumentException("最小IV限制值不能高于最大IV限制值");
        if (values.minEvLimitIndex != null && values.minEvLimitIndex.equals(values.maxEvLimitIndex)
                && values.minEvLimit != null && values.maxEvLimit != null && values.minEvLimit > values.maxEvLimit) throw new IllegalArgumentException("最小努力值限制值不能高于最大努力值限制值");
    }

    private static int readLevel(ByteBufEx buffer) {
        int value = buffer.readUnsignedByte();
        if (value < 1 || value > 100) throw new IllegalArgumentException("非法的宝可梦等级");
        return value;
    }

    private static int readIvIndex(ByteBufEx buffer) {
        int value = buffer.readUnsignedByte();
        if (value > 5) throw new IllegalArgumentException("非法的IV限制索引");
        return value;
    }

    private static int readIvValue(ByteBufEx buffer) {
        int value = buffer.readShortLE();
        if (value < 0 || value > 31) throw new IllegalArgumentException("非法的IV限制值");
        return value;
    }

    private static int readEvIndex(ByteBufEx buffer) {
        int value = buffer.readUnsignedByte();
        if (value > 5) throw new IllegalArgumentException("非法的努力值限制索引");
        return value;
    }

    private static int readEvValue(ByteBufEx buffer) {
        int value = buffer.readUnsignedShortLE();
        if (value > GtlRequestState.MAX_EV_LIMIT) throw new IllegalArgumentException("非法的努力值限制值");
        return value;
    }

    private static int readNonNegativeInt(ByteBufEx buffer, String label) {
        int value = buffer.readIntLE();
        if (value < 0) throw new IllegalArgumentException("非法的" + label);
        return value;
    }

    private static final class Values {
        private GtlGenderFilterType genderFilterType;
        private Integer minPrice;
        private Integer maxPrice;
        private Integer minLevel;
        private Integer maxLevel;
        private Long minEggYearTimestamp;
        private Long maxEggYearTimestamp;
        private PokemonEggGroupType eggGroupFilterType;
        private PokemonNatureType pokemonNatureType;
        private GtlShinyFilterType shinyFilterType;
        private Integer particleFilterType;
        private Integer learnedMoveIndexId;
        private Integer pokemonAbilityIndexId;
        private Integer hiddenAbilityFilterType;
        private Integer alphaFilterType;
        private Integer minIvLimitIndex;
        private Integer minIvLimit;
        private Integer maxIvLimitIndex;
        private Integer maxIvLimit;
        private Integer minEvLimitIndex;
        private Integer minEvLimit;
        private Integer maxEvLimitIndex;
        private Integer maxEvLimit;
        private Integer ivMinMatchCount;
        private boolean hideFemaleOnlySpecies;
        private boolean hideDitto;
        private boolean altFormUnlocked;
        private Integer itemFashionSlot;
    }
}
