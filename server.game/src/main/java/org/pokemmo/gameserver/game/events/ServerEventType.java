package org.pokemmo.gameserver.game.events;

public enum ServerEventType {

    oak_lab_status("Oak_Lab_Status", EventRegionType.KANTO),//大木博士研究所状态
    // 0首次访问 1大木护送至研究所 2御三家可选择 3劲敌准备好对战 4获得大木博士包裹 5交付大木博士包裹 6获得图鉴 7Custom 8获得全国图鉴 9完成所有事件
    oak_parcel_status("Oak_Parcel_Status", EventRegionType.KANTO),//大木博士包裹状态
    // 0未收到 1已收到 2已交付 3Custom
    kanto_first_partner_status("Kanto_First_Partner_Status", EventRegionType.KANTO);//关都初始御三家状态
    // 0妙蛙种子 1杰尼龟 2小火龙 3Custom

    private static ServerEventType[] kantoTypeArray = {oak_lab_status, oak_parcel_status, kanto_first_partner_status};
    private String eventName;
    private EventRegionType eventRegionType;
    public String getEventName(){
        return eventName;
    }
    public EventRegionType getEventRegionType(){
        return eventRegionType;
    }
    public static ServerEventType getByName(String eventName, EventRegionType eventRegionType){
        switch (eventRegionType){
            case KANTO:
                for(ServerEventType type : kantoTypeArray){
                    if(type.getEventName().equals(eventName) && type.getEventRegionType() == eventRegionType){
                        return type;
                    }
                }
                break;
        }
        return null;
    }

    ServerEventType(String eventName, EventRegionType eventRegionType){
        this.eventName = eventName;
        this.eventRegionType = eventRegionType;
    }
}
