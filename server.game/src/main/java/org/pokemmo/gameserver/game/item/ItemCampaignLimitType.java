package org.pokemmo.gameserver.game.item;

public enum ItemCampaignLimitType {
    PUMPKING(0),//南瓜王
    THE_ORIGINAL_YOU(1);//最初的你
    private byte type;
    private static final ItemCampaignLimitType[] allTypeArray = values();
    ItemCampaignLimitType(int type) {
       this.type = (byte) type;
    }
    public byte getType() {
       return type;
    }
    public static ItemCampaignLimitType getByType(byte type) {
       for(ItemCampaignLimitType itemCampaignLimitType : allTypeArray) {
          if(itemCampaignLimitType.type == type) {
             return itemCampaignLimitType;
          }
       }
       return null;
    }
}
