package org.pokemmo.gameserver.game.events;

public enum EventRegionType {
    KANTO(0),//关都
    HOENN(1),//丰原
    UNOVA(2),//合众
    SINNOH(3),//神奥
    CUSTOM(10),//自定义事件区域
    GAMEACTIVEEVENT(128);//游戏活动事件区域
    public static final EventRegionType[] allTypeArray = {KANTO,HOENN,UNOVA,SINNOH,CUSTOM,GAMEACTIVEEVENT};
    private final byte type;
    EventRegionType(int type){
        this.type = (byte) type;
    }
    public static EventRegionType getByType(int regionId){
        for(EventRegionType eventRegion:allTypeArray){
            if(eventRegion.type == (byte)regionId){
                return eventRegion;
            }
        }
        return null;
    }
    public byte getType() {
        return type;
    }
}
