package org.pokemmo.gameserver.game.pokemon;


import java.util.ArrayList;
import java.util.List;

public class JsonPokemonInfoConfig {
    private short id;
    private String name;
    private byte exp_type;
    private boolean obtainable;
    private short gender_ratio;
    private short height;
    private short weight;
    private String[] egg_groups;
    private List<PokemonAbilityConfig> abilities;
    private List<PokemonFormConfig> forms;
    private List<PokemonEvolutionConfig> evolutions;
    private List<PokemonMoveConfig> moves;
    private String[] types;
    private PokemonStatConfig stats;
    private PokemonYieldConfig yields;
    private String[] tiers;
    private List<PokemonMayHeldItemConfig> held_items;
    private List<PokemonLocationConfig> locations;
    public short getId() {
        return id;
    }
    public PokemonDexData toPokemonInfo() {
        return new PokemonDexData(id, name, PokemonGetExpSpeedType.getByType(exp_type), obtainable, gender_ratio, height, weight, toEggGroupTypeList(), toPokemonAbilities(), toPokemonForms(), toPokemonEvolutions(), toPokemonMoves(), toPokemonTypes(), stats.toPokemonStat(), yields.toPokemonYield(), tiers, toPokemonHeldItems(), toPokemonLocations());
    }
    private List<PokemonEggGroupType> toEggGroupTypeList(){
        List<PokemonEggGroupType> eggGroupTypeList = new ArrayList<>(this.egg_groups.length);
        for(String eggGroup : this.egg_groups){
             switch (eggGroup) {
                 case "怪兽组":
                     eggGroupTypeList.add(PokemonEggGroupType.MONSTER);
                     break;
                 case "水1组":
                     eggGroupTypeList.add(PokemonEggGroupType.WATER_1);
                     break;
                 case "虫组":
                     eggGroupTypeList.add(PokemonEggGroupType.BUG);
                     break;
                 case "飞行组":
                     eggGroupTypeList.add(PokemonEggGroupType.FLY);
                     break;
                 case "陆上组":
                     eggGroupTypeList.add(PokemonEggGroupType.FIELD);
                     break;
                 case "妖精组":
                     eggGroupTypeList.add(PokemonEggGroupType.FAIRY);
                     break;
                 case "植物组":
                     eggGroupTypeList.add(PokemonEggGroupType.GRASS);
                     break;
                 case "人形组":
                     eggGroupTypeList.add(PokemonEggGroupType.HUMAN_LIKE);
                     break;
                 case "水3组":
                     eggGroupTypeList.add(PokemonEggGroupType.WATER_3);
                     break;
                 case "矿物组":
                     eggGroupTypeList.add(PokemonEggGroupType.MINERAL);
                     break;
                 case "不定形组":
                     eggGroupTypeList.add(PokemonEggGroupType.AMORPHOUS);
                     break;
                 case "水2组":
                     eggGroupTypeList.add(PokemonEggGroupType.WATER_2);
                     break;
                 case "[132]百变怪":
                     eggGroupTypeList.add(PokemonEggGroupType.DITTO);
                     break;
                 case "龙组":
                     eggGroupTypeList.add(PokemonEggGroupType.DRAGON);
                     break;
                 case "未发现蛋组":
                     eggGroupTypeList.add(PokemonEggGroupType.UNKNOWN);
                     break;
                 case "无性别":
                     eggGroupTypeList.add(PokemonEggGroupType.NOSEX);
                     break;
             }
        }
        return eggGroupTypeList;
    }
    private List<PokemonAbility> toPokemonAbilities(){
        List<PokemonAbility> pokemonAbilities = new ArrayList<>(this.abilities.size());
        for(PokemonAbilityConfig abilityConfig : this.abilities){
            pokemonAbilities.add(abilityConfig.toPokemonAbility());
        }
        return pokemonAbilities;
    }
    private List<PokemonForm> toPokemonForms(){
        if(this.forms == null){
            return null;
        }
        List<PokemonForm> pokemonForms = new ArrayList<>(this.forms.size());
        for(PokemonFormConfig formConfig : this.forms){
            pokemonForms.add(formConfig.toPokemonForm());
        }
        return pokemonForms;
    }
    private List<PokemonEvolution> toPokemonEvolutions(){
        List<PokemonEvolution> pokemonEvolutions = new ArrayList<>(this.evolutions.size());
        for(PokemonEvolutionConfig evolutionConfig : this.evolutions){
            pokemonEvolutions.add(evolutionConfig.toPokemonEvolution());
        }
        return pokemonEvolutions;
    }
    private List<PokemonMove> toPokemonMoves(){
        List<PokemonMove> pokemonMoves = new ArrayList<>(this.moves.size());
        for(PokemonMoveConfig moveConfig : this.moves){
            pokemonMoves.add(moveConfig.toPokemonMove());
        }
        return pokemonMoves;
    }
    private List<PokemonType> toPokemonTypes(){
        List<PokemonType> pokemonTypes = new ArrayList<>(this.types.length);
        for(String type : this.types){
            switch (type){
                case "NORMAL":
                    pokemonTypes.add(PokemonType.NORMAL);
                    break;
                case "FIGHTING":
                    pokemonTypes.add(PokemonType.FIGHTING);
                    break;
                case "FLYING":
                    pokemonTypes.add(PokemonType.FLYING);
                    break;
                case "POISON":
                    pokemonTypes.add(PokemonType.POISON);
                    break;
                case "GROUND":
                    pokemonTypes.add(PokemonType.GROUND);
                    break;
                case "ROCK":
                    pokemonTypes.add(PokemonType.ROCK);
                    break;
                case "BUG":
                    pokemonTypes.add(PokemonType.BUG);
                    break;
                case "GHOST":
                    pokemonTypes.add(PokemonType.GHOST);
                    break;
                case "STEEL":
                    pokemonTypes.add(PokemonType.STEEL);
                    break;
                case "FIRE":
                    pokemonTypes.add(PokemonType.FIRE);
                    break;
                case "WATER":
                    pokemonTypes.add(PokemonType.WATER);
                    break;
                case "GRASS":
                    pokemonTypes.add(PokemonType.GRASS);
                    break;
                case "ELECTRIC":
                    pokemonTypes.add(PokemonType.ELECTRIC);
                    break;
                case "PSYCHIC":
                    pokemonTypes.add(PokemonType.PSYCHIC);
                    break;
                case "ICE":
                    pokemonTypes.add(PokemonType.ICE);
                    break;
                case "DRAGON":
                    pokemonTypes.add(PokemonType.DRAGON);
                    break;
                case "DARK":
                    pokemonTypes.add(PokemonType.DARK);
                    break;
                case "FAIRY":
                    pokemonTypes.add(PokemonType.FAIRY);
                    break;
                case "NONE":
                    pokemonTypes.add(PokemonType.NONE);
                    break;
                default:
                    pokemonTypes.add(PokemonType.QUESTION);
                    break;
            }
        }
        return pokemonTypes;
    }
    private List<PokemonMayHeldItem> toPokemonHeldItems(){
        List<PokemonMayHeldItem> pokemonMayHeldItems = new ArrayList<>(this.held_items.size());
        for(PokemonMayHeldItemConfig heldItemConfig : this.held_items){
            pokemonMayHeldItems.add(heldItemConfig.toItemInfo());
        }
        return pokemonMayHeldItems;
    }
    private List<PokemonLocation> toPokemonLocations(){
        List<PokemonLocation> pokemonLocations = new ArrayList<>(this.locations.size());
        for(PokemonLocationConfig locationConfig : this.locations){
            pokemonLocations.add(locationConfig.toPokemonLocation());
        }
        return pokemonLocations;
    }
}
