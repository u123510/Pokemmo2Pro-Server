package org.pokemmo.gameserver.game.character;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.gameserver.game.entity.EntityNameplateType;
import org.pokemmo.gameserver.game.entity.PlayerEntity;
import org.pokemmo.gameserver.game.item.ItemRarityType;
import org.pokemmo.gameserver.game.permission.PermissionType;
import org.pokemmo.gameserver.game.skin.SkinData;
import org.pokemmo.gameserver.util.ArrayUtil;

import java.time.LocalDateTime;

@Getter @Setter @AllArgsConstructor
public class CharacterData implements CharacterDataComparator {
    // 角色实体信息
    private PlayerEntity playerEntity;
    private int accountId;
    private LocalDateTime loginTimeStamp;
    private long lastLoginMac;
    private LocalDateTime createTimeStamp;
    private int onlineMinutes;
    // 角色资产信息
    private int money;
    private int coins;
    private short battlePoints;
    private short safariSteps;
    private short safariBallAmount;
    private short otherSettingsValue;
    private short pcBoxExpansionNumber;
    private short battleBoxExpansionNumber;
    private short templateAmount;
    private short repelSteps;
    private short repelItemId;
    private ItemRarityType lureRarityType;
    private short lureItemId;
    private short lureSteps;
    //玩家的皮肤信息
    private SkinData skinData;
    //玩家的频道
    //TODO 根据负载对玩家的频道进行分配，防止某个地图玩家过多
    private final short channel = 0;
    //五地区是否成为冠军
    private boolean[] championFlag;
    //五地区是否有跑步鞋
    private boolean[] runningShoeFlag;
    //五地区的徽章状态
    private short[] badgeFlag;
    //五地区城市是否可以飞行
    private long[] cityCanFlyFlag;
    //五地区的故事线状态
    private short[] storyLineFlag;
    //五地区副本完成次数
    private short[] instanceTimes;
    //五地区副本下次可完成时间
    private LocalDateTime[] instanceTimesStamp;
    //五地区御三家选择
    private short[] firstPartnerStatus;
    /*关都地区服务器存贮事件*/
    //关都实验室状态
    private short oakLabStatus;
    //关都实验室包裹状态
    private short oakParcelStatus;
    public static class Builder {
        private long characterId;
        private int accountId;
        private String characterName;
        private String unionName;
        private short characterSex;
        private LocalDateTime loginTimeStamp;
        private long lastLoginMac;
        private LocalDateTime createTimeStamp;
        private int onlineMinutes;
        // 角色资产信息
        private int money;
        private int coins;
        private short battlePoints;
        private short safariSteps;
        private short safariBallAmount;
        private short otherSettingsValue;
        private short pcBoxExpansionNumber;
        private short battleBoxExpansionNumber;
        private short templateAmount;
        private short repelSteps;
        private short repelItemId;
        private ItemRarityType lureRarityType;
        private short lureItemId;
        private short lureSteps;
        // 角色位置信息
        private short regionIndexId;
        private short mapHeaderIdOrGbaMapGroupId;
        private short gbaMapId;
        private short x;
        private short y;
        private short z;
        private short toward;
        private short transportation;
        private EntityNameplateType entityNameplateType;
        // 角色权限信息
        private PermissionType permission;
        // 加载的rom模型信息
        private short modelRegionIndexId;
        private short modelIndexId;
        //宝可梦跟随数据
        private short followPokemonId;
        private short followPokemonRarity;
        //玩家的皮肤信息
        private short forehead;
        private short foreheadColor;
        private short hat;
        private short hatColor;
        private short hair;
        private short hairColor;
        private short eyes;
        private short eyesColor;
        private short facialHair;
        private short facialHairColor;
        private short back;
        private short backColor;
        private short top;
        private short topColor;
        private short gloves;
        private short glovesColor;
        private short footwear;
        private short footwearColor;
        private short leggings;
        private short leggingsColor;
        private short fishingRod;
        private short bike;
        //五地区是否成为冠军
        private boolean[] championFlag;
        //五地区是否有跑步鞋
        private boolean[] runningShoeFlag;
        //五地区的徽章状态
        private short[] badgeFlag;
        //五地区城市是否可以飞行
        private long[] cityCanFlyFlag;
        //五地区的故事线状态
        private short[] storyLineFlag;
        //五地区副本完成次数
        private short[] instanceTimes;
        //五地区副本下次可完成时间
        private LocalDateTime[] instanceTimesStamp;
        //五地区御三家选择
        private short[] firstPartnerStatus;
        /*关都地区服务器存贮事件*/
        //关都实验室状态
        private short oakLabStatus;
        //关都实验室包裹状态
        private short oakParcelStatus;
        public Builder setByRecord(CharacterRecord characterRecord) {
            this.characterId = characterRecord.getId();
            this.accountId = characterRecord.getAccountId();
            this.characterName = characterRecord.getName();
            this.unionName = characterRecord.getUnionName();
            this.characterSex = characterRecord.getSex();
            this.loginTimeStamp = characterRecord.getLoginTimeStamp();
            this.lastLoginMac = characterRecord.getLastLoginMac();
            this.createTimeStamp = characterRecord.getCreatedAt();
            this.onlineMinutes = characterRecord.getOnlineMinutes();
            this.money = characterRecord.getMoney();
            this.coins = characterRecord.getCoins();
            this.battlePoints = characterRecord.getBattlePoints();
            this.safariSteps = characterRecord.getSafariSteps();
            this.safariBallAmount = characterRecord.getSafariBallAmount();
            this.otherSettingsValue = characterRecord.getOtherSettingsValue();
            this.pcBoxExpansionNumber = characterRecord.getPcBoxExpansionNumber();
            this.battleBoxExpansionNumber = characterRecord.getBattleBoxExpansionNumber();
            this.templateAmount = characterRecord.getTemplateAmount();
            this.repelSteps = characterRecord.getRepelSteps();
            this.repelItemId = characterRecord.getRepelItemId();
            this.lureRarityType = ItemRarityType.getByType(characterRecord.getLureType());
            this.lureItemId = characterRecord.getLureItemId();
            this.lureSteps = characterRecord.getLureSteps();
            this.regionIndexId = characterRecord.getRegionId();
            this.mapHeaderIdOrGbaMapGroupId = characterRecord.getMapHeaderIdOrGbaMapGroupId();
            this.gbaMapId = characterRecord.getGbaMapId();
            this.x = characterRecord.getX();
            this.y = characterRecord.getY();
            this.z = characterRecord.getZ();
            this.toward = characterRecord.getToward();
            this.transportation = characterRecord.getTransportation();
            this.entityNameplateType = EntityNameplateType.getByType(characterRecord.getNameplateStatus());
            this.permission = PermissionType.getByType(characterRecord.getPermission());
            this.modelRegionIndexId = characterRecord.getModelRegionIndexId();
            this.modelIndexId = characterRecord.getModelIndexId();
            this.followPokemonId = characterRecord.getFollowerPokemonIndexId();
            this.followPokemonRarity = characterRecord.getFollowerPokemonRarity();
            this.forehead = characterRecord.getForehead();
            this.foreheadColor = characterRecord.getForeheadColor();
            this.hat = characterRecord.getHat();
            this.hatColor = characterRecord.getHatColor();
            this.hair = characterRecord.getHair();
            this.hairColor = characterRecord.getHairColor();
            this.eyes = characterRecord.getEyes();
            this.eyesColor = characterRecord.getEyesColor();
            this.facialHair = characterRecord.getFacialHair();
            this.facialHairColor = characterRecord.getFacialHairColor();
            this.back = characterRecord.getBack();
            this.backColor = characterRecord.getBackColor();
            this.top = characterRecord.getTop();
            this.topColor = characterRecord.getTopColor();
            this.gloves = characterRecord.getGloves();
            this.glovesColor = characterRecord.getGlovesColor();
            this.footwear = characterRecord.getFootwear();
            this.footwearColor = characterRecord.getFootwearColor();
            this.leggings = characterRecord.getLeggings();
            this.leggingsColor = characterRecord.getLeggingsColor();
            this.fishingRod = characterRecord.getFishingRod();
            this.bike = characterRecord.getBike();
            this.championFlag = ArrayUtil.toBooleanPrimitive(characterRecord.getChampionFlag());
            this.runningShoeFlag = ArrayUtil.toBooleanPrimitive(characterRecord.getRunningShoeFlag());
            this.badgeFlag = ArrayUtil.toShortPrimitive(characterRecord.getBadgeFlag());
            this.cityCanFlyFlag = ArrayUtil.toLongPrimitive(characterRecord.getCityCanFlyFlag());
            this.storyLineFlag = ArrayUtil.toShortPrimitive(characterRecord.getStoryLineFlag());
            this.instanceTimes = ArrayUtil.toShortPrimitive(characterRecord.getInstanceTimes());
            this.instanceTimesStamp = characterRecord.getInstanceNextTime();
            this.firstPartnerStatus = ArrayUtil.toShortPrimitive(characterRecord.getFirstPartnerStatus());
            this.oakLabStatus = characterRecord.getOakLabStatus();
            this.oakParcelStatus = characterRecord.getOakParcelStatus();
            return this;
        }
        public CharacterData build(){
            PlayerEntity playerEntity = new PlayerEntity(characterId,regionIndexId,mapHeaderIdOrGbaMapGroupId,gbaMapId,x,y,z,toward,characterSex,characterName,transportation,entityNameplateType,permission,modelRegionIndexId,modelIndexId,followPokemonId,followPokemonRarity,unionName);
            SkinData skinData = new SkinData(forehead,foreheadColor,hat,hatColor,hair,hairColor,eyes,eyesColor,facialHair,facialHairColor,back,backColor,top,topColor,gloves,glovesColor,footwear,footwearColor,leggings,leggingsColor,fishingRod,bike);
            return new CharacterData(playerEntity,accountId,loginTimeStamp,lastLoginMac,createTimeStamp,onlineMinutes,money,coins,battlePoints,safariSteps,safariBallAmount,otherSettingsValue,pcBoxExpansionNumber,battleBoxExpansionNumber,templateAmount,repelSteps,repelItemId,lureRarityType,lureItemId,lureSteps,skinData,championFlag,runningShoeFlag,badgeFlag,cityCanFlyFlag,storyLineFlag,instanceTimes,instanceTimesStamp,firstPartnerStatus,oakLabStatus,oakParcelStatus);
        }
    }
    @Override
    public boolean equals(CharacterData character) {
         return this.playerEntity.getEntityGameId() == character.getPlayerEntity().getEntityGameId();
    }
}
