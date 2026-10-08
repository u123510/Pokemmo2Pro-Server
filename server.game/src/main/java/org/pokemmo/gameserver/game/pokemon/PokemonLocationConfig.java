package org.pokemmo.gameserver.game.pokemon;

public class PokemonLocationConfig {
    private String type;
    private byte region_id;
    private String region_name;
    private String location;
    private short min_level;
    private short max_level;
    private String rarity;
    public PokemonLocation toPokemonLocation(){
        return new PokemonLocation(toPokemonAppearLoactionType(), region_id, min_level, max_level, toPokemonRarityLevelType());
    }
    private PokemonAppearLoactionType toPokemonAppearLoactionType(){
        switch (this.type){
            case "草地":
                return PokemonAppearLoactionType.GRASS;
            case "水面":
                return PokemonAppearLoactionType.WATER;
            case "岩石":
                return PokemonAppearLoactionType.ROCK;
            case "钓鱼":
                return PokemonAppearLoactionType.FISH;
            case "[5446]好钓竿":
                return PokemonAppearLoactionType.GOOD_ROD;
            case "[5447]厉害钓竿":
                return PokemonAppearLoactionType.SUPER_ROD;
            case "深草丛":
                return PokemonAppearLoactionType.DARK_GRASS;
            case "山洞":
                return PokemonAppearLoactionType.CAVE;
            case "内部":
                return PokemonAppearLoactionType.INSIDE;
            case "影子":
                return PokemonAppearLoactionType.SHADE;
            case "卷尘地面":
                return PokemonAppearLoactionType.DUST_CLOUD;
            case "甜甜蜜树":
                return PokemonAppearLoactionType.HONEY_TREE;
            case "头锤":
                return PokemonAppearLoactionType.HEADBUTT;
            default:
                return null;
        }
    }
    private PokemonRarityLevelType toPokemonRarityLevelType(){
        switch (rarity){
            case "非常常见":
                return PokemonRarityLevelType.VERY_COMMON;
            case "常见":
                return PokemonRarityLevelType.COMMON;
            case "少见":
                return PokemonRarityLevelType.UNCOMMON;
            case "稀有":
                return PokemonRarityLevelType.RARE;
            case "非常稀有":
                return PokemonRarityLevelType.VERY_RARE;
            case "特殊":
                return PokemonRarityLevelType.SPECIAL;
            case "群怪":
                return PokemonRarityLevelType.HORDE;
            case "香水":
                return PokemonRarityLevelType.LURE;
            default:
                return null;
        }
    }
}
