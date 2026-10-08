package org.pokemmo.gameserver.game.item;

public enum FashionLimitType {
    NONE(0),
    PVP_REWARD_LIMIT(1),
    XMAS_SPEC_LIMIT(2),//圣诞节特殊限定
    HOLIDAY_LIMIT_BOX_OPEN(3),//节日礼物打开获得
    LIMITED(4),//限定
    HOLLOW_SEASONAL_LIMIT(5),//万圣节节日限定
    XMAS_SEASONAL_LIMIT(6),//圣诞节节日限定
    HOLIDAY_INSTANCE_LIMIT(7),//节日副本名次限定
    GM_LEVEL_LIMIT2(8),//GM等级限定
    PVP_LEVEL_RANK_LIMIT(9),//PVP位次限定
    PVP_CHAMPIONSHIP_RANK_LIMIT(10),//PVP锦标赛位次限定
    PVP_SEASON_RANK_LIMIT(11),//PVP赛季位次限定
    PVE_MINESWEEPER_LIMIT(12),//PVE电玩城扫雷限定
    SHINY_WAR_RANK_LIMIT(13);//闪光战争位次限定
    private byte type;
    private static FashionLimitType[] allTypeArray = values();
    FashionLimitType(int type) {
       this.type = (byte) type;
    }
    public byte getType(){
        return type;
    }
    public static FashionLimitType getByType(int type){
        for(FashionLimitType fashionLimitType : allTypeArray){
            if(fashionLimitType.type == (byte) type){
                return fashionLimitType;
            }
        }
        return null;
    }

}
