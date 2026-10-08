package org.pokemmo.gameserver.game.events;


public enum GameEventType {
    two_island_arcade_status("two_island_arcade_status",2,EventRegionType.KANTO,EventKindType.STORY_LINE,675),//3岛解救小迷，2岛游戏厅解锁刚刚
    boulder_badge("boulder_badge",0,EventRegionType.KANTO,EventKindType.BADGE,2080),//岩石徽章 尼比 小刚
    cascade_badge("cascade_badge",1,EventRegionType.KANTO,EventKindType.BADGE,2081),//水徽章 华蓝 小霞
    thunder_badge("thunder_badge",2,EventRegionType.KANTO,EventKindType.BADGE,2082),//电徽章 枯叶 马志士
    rainbow_badge("rainbow_badge",3,EventRegionType.KANTO,EventKindType.BADGE,2083),//彩虹徽章 彩虹 莉佳
    soul_badge("soul_badge",4,EventRegionType.KANTO,EventKindType.BADGE,2084),//粉红徽章 浅红 阿桔
    marsh_badge("marsh_badge",5,EventRegionType.KANTO,EventKindType.BADGE,2085),//金色徽章 金黄 娜兹
    volcano_badge("volcano_badge",6,EventRegionType.KANTO,EventKindType.BADGE,2086),//深红徽章 红莲岛 夏伯
    earth_badge("earth_badge",7,EventRegionType.KANTO,EventKindType.BADGE,2087),//大地徽章 常磐 板木
    the_hall_of_fame_status("the_hall_of_fame_status",0,EventRegionType.KANTO,EventKindType.FAME,2092),// 关都地区是否击败冠军进入名人堂
    running_shoe_status("running_shoe_status",0,EventRegionType.KANTO,EventKindType.RUNNING_SHOE,2095),//跑步鞋状态 跑步鞋
    connect_with_hoenn_status("connect_with_hoenn_status",1,EventRegionType.KANTO,EventKindType.STORY_LINE,2116),//与丰原连接的状态，在一岛蓝宝石剧情完成后，修复机器，此时连接为true
    tanoby_key_status("tanoby_key_status",3,EventRegionType.KANTO,EventKindType.STORY_LINE,2121),//阿斯卡纳的锁状态，玩家解密完成后，7岛七个遗迹解锁未知图腾
    kanto_insland_status("kanto_insland_status",0,EventRegionType.KANTO,EventKindType.STORY_LINE,2228),//关都岛屿首次解锁状态
    pallet_town_can_fly("pallet_town_can_fly",0,EventRegionType.KANTO,EventKindType.CITY_FLY,2192),//真新镇可飞
    viridian_city_can_fly("viridian_city_can_fly",1,EventRegionType.KANTO,EventKindType.CITY_FLY,2193),//常磐市可飞状态
    pewter_city_can_fly("pewter_city_can_fly",2,EventRegionType.KANTO,EventKindType.CITY_FLY,2194),//尼比市可飞状态
    cerulean_city_can_fly("cerulean_city_can_fly",3,EventRegionType.KANTO,EventKindType.CITY_FLY,2195),//华蓝市可飞状态
    lavender_town_can_fly("lavender_town_can_fly",4,EventRegionType.KANTO,EventKindType.CITY_FLY,2196),//紫苑镇可飞状态
    vermilion_city_can_fly("vermilion_city_can_fly",5,EventRegionType.KANTO,EventKindType.CITY_FLY,2197),//枯叶市可飞状态
    celadon_city_can_fly("celadon_city_can_fly",6,EventRegionType.KANTO,EventKindType.CITY_FLY,2198),//彩虹市可飞状态
    fuchsia_city_can_fly("fuchsia_city_can_fly",7,EventRegionType.KANTO,EventKindType.CITY_FLY,2199),//浅红市可飞状态
    cinnabar_island_can_fly("cinnabar_island_can_fly",8,EventRegionType.KANTO,EventKindType.CITY_FLY,2200),//红莲岛可飞状态
    indigo_plateau_can_fly("indigo_plateau_can_fly",9,EventRegionType.KANTO,EventKindType.CITY_FLY,2201),//石英高原可飞状态
    saffron_city_can_fly("saffron_city_can_fly",10,EventRegionType.KANTO,EventKindType.CITY_FLY,2202),//金黄市可飞状态
    one_island_can_fly("one_island_can_fly",11,EventRegionType.KANTO,EventKindType.CITY_FLY,2203),//一之屿可飞状态
    two_island_can_fly("two_island_can_fly",12,EventRegionType.KANTO,EventKindType.CITY_FLY,2204),//二之岛可飞状态
    three_island_can_fly("three_island_can_fly",13,EventRegionType.KANTO,EventKindType.CITY_FLY,2205),//三之岛可飞状态
    four_island_can_fly("four_island_can_fly",14,EventRegionType.KANTO,EventKindType.CITY_FLY,2206),//四之岛可飞状态
    five_island_can_fly("five_island_can_fly",15,EventRegionType.KANTO,EventKindType.CITY_FLY,2207),//五之岛可飞状态
    six_island_can_fly("six_island_can_fly",16,EventRegionType.KANTO,EventKindType.CITY_FLY,2208),//六之岛可飞状态
    seven_island_can_fly("seven_island_can_fly",17,EventRegionType.KANTO,EventKindType.CITY_FLY,2209),//七之岛可飞状态
    route_4_can_fly("route_4_can_fly",18,EventRegionType.KANTO,EventKindType.CITY_FLY,2210);//四号道路可飞状态
    public static final GameEventType[] kantoStoryLineArray = {two_island_arcade_status,connect_with_hoenn_status,tanoby_key_status,kanto_insland_status};
    public static final GameEventType[] kantoBadgeArray = {boulder_badge,cascade_badge,thunder_badge,rainbow_badge,soul_badge,marsh_badge,volcano_badge,earth_badge};
    public static final GameEventType[] kantoCityCanFlyArray = {pallet_town_can_fly,viridian_city_can_fly,pewter_city_can_fly,cerulean_city_can_fly,lavender_town_can_fly,vermilion_city_can_fly,celadon_city_can_fly,fuchsia_city_can_fly,cinnabar_island_can_fly,indigo_plateau_can_fly,saffron_city_can_fly,one_island_can_fly,two_island_can_fly,three_island_can_fly,four_island_can_fly,five_island_can_fly,six_island_can_fly,seven_island_can_fly,route_4_can_fly};
    public static final GameEventType[] kantoFameArray = {the_hall_of_fame_status};
    public static final GameEventType[] kantoEventArray= {boulder_badge,cascade_badge,thunder_badge,rainbow_badge,soul_badge,marsh_badge,volcano_badge,earth_badge,running_shoe_status,pallet_town_can_fly,viridian_city_can_fly,pewter_city_can_fly,cerulean_city_can_fly,lavender_town_can_fly,vermilion_city_can_fly,celadon_city_can_fly,fuchsia_city_can_fly,cinnabar_island_can_fly,indigo_plateau_can_fly,saffron_city_can_fly,one_island_can_fly,two_island_can_fly,three_island_can_fly,four_island_can_fly,five_island_can_fly,six_island_can_fly,seven_island_can_fly,route_4_can_fly};
    private String eventName;
    private int index;
    private EventRegionType eventRegionType;
    private EventKindType eventKindType;
    private short eventFlagId;
    GameEventType(String eventName, int index,EventRegionType eventRegion, EventKindType eventKind, int eventFlagId){
        this.eventName = eventName;
        this.index = index;
        this.eventRegionType = eventRegion;
        this.eventKindType = eventKind;
        this.eventFlagId = (short) eventFlagId;
    }
    public EventRegionType getEventRegionType(){
        return this.eventRegionType;
    }
    public EventKindType getEventKindType(){
        return this.eventKindType;
    }
    public int getIndex(){
        return this.index;
    }
    public short getEventFlagId(){
        return this.eventFlagId;
    }
    public static GameEventType getByName(String eventName, EventRegionType regionType){
        switch (regionType){
            case KANTO:
                for(GameEventType gameSideEventFlag :kantoEventArray){
                    if(gameSideEventFlag.eventName.equals(eventName)){
                        return gameSideEventFlag;
                    }
                }
        }
        return null;
    }
}
