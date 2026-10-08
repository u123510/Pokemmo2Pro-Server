package org.pokemmo.gameserver.game.pokemon;
import org.pokemmo.gameserver.game.move.MoveLearnConditionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PokemonDexData {
    private short pokemonIndexId;
    private String pokemonName;
    private PokemonGetExpSpeedType getExpSpeedType;
    private boolean obtainable;
    private short genderRatio;
    private short height;
    private short weight;
    private List<PokemonEggGroupType> eggGroup;
    private List<PokemonAbility> pokemonAbilities;
    private List<PokemonForm> pokemonForms;
    private List<PokemonEvolution> pokemonEvolutions;
    private List<PokemonMove> pokemonCanLearnMoves;
    private List<PokemonType> pokemonTypes;
    private PokemonStat pokemonStats;
    private PokemonYield pokemonYield;
    private String[] tiers;
    private List<PokemonMayHeldItem> mayHeldItems;
    private List<PokemonLocation> locations;
    public PokemonDexData(short pokemonIndexId, String pokemonName, PokemonGetExpSpeedType getExpSpeedType, boolean obtainable, short genderRatio, short height, short weight, List<PokemonEggGroupType> eggGroup, List<PokemonAbility> pokemonAbilities, List<PokemonForm> pokemonForms, List<PokemonEvolution> pokemonEvolutions, List<PokemonMove> pokemonCanLearnMoves, List<PokemonType> pokemonTypes, PokemonStat pokemonStats, PokemonYield pokemonYield, String[] tiers, List<PokemonMayHeldItem> mayHeldItems, List<PokemonLocation> locations) {
        this.pokemonIndexId = pokemonIndexId;
        this.pokemonName = pokemonName;
        this.getExpSpeedType = getExpSpeedType;
        this.obtainable = obtainable;
        this.genderRatio = genderRatio;
        this.height = height;
        this.weight = weight;
        this.eggGroup = eggGroup;
        this.pokemonAbilities = pokemonAbilities;
        this.pokemonForms = pokemonForms;
        this.pokemonEvolutions = pokemonEvolutions;
        this.pokemonCanLearnMoves = pokemonCanLearnMoves;
        this.pokemonTypes = pokemonTypes;
        this.pokemonStats = pokemonStats;
        this.pokemonYield = pokemonYield;
        this.tiers = tiers;
        this.mayHeldItems = mayHeldItems;
        this.locations = locations;
    }
    public short[] genderWildPokemonMoves(short pokemonLevel) {
        short[] moves = new short[4];
        ArrayList<PokemonMove> filterMoves = new ArrayList<>();
        for(PokemonMove move:pokemonCanLearnMoves){
            if(move.getMoveLearnConditionType() == MoveLearnConditionType.LEVEL) {
                if(move.getLearnNeedLevel() <= pokemonLevel)
                    filterMoves.add(move);
            }
        }
        //去重
        filterMoves.stream().distinct().collect(Collectors.toList());
        //逆序排序，按照学习等级从高到低
        filterMoves.sort(Comparator.comparing(PokemonMove::getLearnNeedLevel).reversed());
        //填充返回的moves数组，最多4个
        for (int i = 0; i < Math.min(filterMoves.size(), 4); i++) {
            moves[i] = filterMoves.get(i).getPokemonMoveIndexId();
        }
        return moves;
    }
    public short getPokemonAbilityValue(PokemonStatType statType, int pokemonIv,int pokemonEv,int level,PokemonNatureType natureType) {
        if (statType == null) {
            return 0;
        }
        short pokemonStat = getPokemonStats().getByStatType(statType);
        double natureMultiplier = natureType == null ? 1.0d : natureType.getStatMultiplier(statType);
        return (short) (((((((pokemonEv / 4) + ((pokemonStat * 2) + pokemonIv)) + statType.getLevelCorrectedParameter()) * level) / 100) + statType.getCorrectedParameter()) * natureMultiplier);
    }
    public short getPokemonIndexId() {
        return pokemonIndexId;
    }

    public String getPokemonName() {
        return pokemonName;
    }

    public PokemonGetExpSpeedType getGetExpSpeedType() {
        return getExpSpeedType;
    }

    public boolean isObtainable() {
        return obtainable;
    }

    public short getGenderRatio() {
        return genderRatio;
    }

    public short getHeight() {
        return height;
    }

    public short getWeight() {
        return weight;
    }

    public List<PokemonEggGroupType> getEggGroup() {
        return eggGroup;
    }

    public List<PokemonAbility> getPokemonAbilities() {
        return pokemonAbilities;
    }

    public List<PokemonForm> getPokemonForms() {
        return pokemonForms;
    }

    public List<PokemonEvolution> getPokemonEvolutions() {
        return pokemonEvolutions;
    }
    public List<PokemonMove> getPokemonCanLearnMoves() {
        return pokemonCanLearnMoves;
    }

    public List<PokemonType> getPokemonTypes() {
        return pokemonTypes;
    }

    public PokemonStat getPokemonStats() {
        return pokemonStats;
    }

    public PokemonYield getPokemonYield() {
        return pokemonYield;
    }

    public String[] getTiers() {
        return tiers;
    }

    public List<PokemonMayHeldItem> getMayHeldItems() {
        return mayHeldItems;
    }
    public List<PokemonLocation> getLocations() {
        return locations;
    }
}
