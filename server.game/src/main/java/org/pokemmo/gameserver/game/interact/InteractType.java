package org.pokemmo.gameserver.game.interact;

public enum InteractType {
    NONE(0),
    SENCE(1),//场景互动
    GAME_ENTITY(2),//游戏实体
    EVENT(3),//事件互动
    TRADE_REQUEST(4),//交易请求确认框
    FRIEND_REQUEST(5),//好友请求确认框
    PC_MENU(6),//宝可梦中心电脑功能菜单
    BATTLE_REQUEST(7),//单挑请求确认框
    SHOP(8),//已协商协议的商店报价会话
    STORY(9);//玩家独立剧情会话，不是客户端 opcode
    private byte type;
    InteractType(int type){
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
}
