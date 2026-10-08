package org.pokemmo.gameserver.game.item;



import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonType;

import java.util.List;
@Getter @Setter @AllArgsConstructor
public class ItemData {
   private short itemIndexId;
   private byte regionIndexId;
   private ItemType itemType;
   private byte itemTypeInfoFlag;
   private ItemRarityType itemRarityType;
   private boolean isItemGTLMaxPriceLimit;
   private boolean isBindAccount;
   private boolean isTradeAble;
   private boolean itemCanGiveToPokemon;
   private boolean isDestoryable;
   private boolean canUseOnPokemon;
   private boolean canDestoryForever;
   private boolean isRomOriginalFashion;
   private short itemMaxStackSize;
   private short itemIncreaseFriendshipValue;
   private byte itemIncreasePokemonLevelValue;
   private FashionLimitType itemFashionLimitType;
   private ItemEffectType itemFirstEffectType;
   private ItemEffectType itemSecondEffectType;
   private byte specItemLimitUseTimes;
   private int itemShopBuyPrice;
   private int itemShopSellPrice;
   private short itemLimitYear;
   private byte itemLimitMonth;
   private short itemSummonPokemonIndexId;
   private List<Short> itemLimitUsePokemons = new java.util.ArrayList<>();
   private byte seedFlavorType;
   private byte seedFlavorValue;
   private short berryItemIndexId;
   private short berryGrowMinutes;
   private short berryWitheredTime;
   private byte itemBerryEffectFlag;
   private byte berryThirstyType;
   private byte berryWiltsSpeedType;
   private byte berryGrowTimeType;
   private PokemonType berryPokemonType;
   private byte berryMainFlavor;
   private List<Short> berryFlavorArray = new java.util.ArrayList<>();
   private short itemMoveIndexId;
   private short itemMoveBasePower;
   private short itemMoveIconIndexId;
   private short itemBuffId;
   private int itemExpValue;
   private byte itemEvChangeType;
   private short itemEvChangeValue;
   private byte itemHolidayLimitType;
   private byte itemFriendshipFlavorType;
   private boolean isRecoverHpPercent;
   private byte itemStoryLineItemHolidayType;
   private byte dyeMinColor;
   private byte dyeMaxColor;
   private byte itemBallType;
   private ItemCampaignLimitType itemCampaignLimitType;
   private byte itemCureStatusConditionValue;
   private ParticleEffectType itemParticleEffectType;
   private byte itemRecoverPpValue;
   private short itemRecoverHpValue;
   private List<ItemAdditionStringData> itemAdditionStringDatas;

   /** Item.bin uses -1 (encoded as 255) for items without a capture-ball type. */
   public boolean isCaptureBall() {
      return itemBallType >= 0;
   }

   public static class Builder {
      private short itemIndexId;
      private byte regionIndexId;
      private ItemType itemType;
      private byte itemTypeInfoFlag;
      private ItemRarityType itemRarityType;
      private boolean isItemGTLMaxPriceLimit;
      private boolean isBindAccount;
      private boolean isTradeAble;
      private boolean itemCanGiveToPokemon;
      private boolean isDestoryable;
      private boolean canUseOnPokemon;
      private boolean canDestoryForever;
      private boolean isRomOriginalFashion;
      private short itemMaxStackSize;
      private short itemIncreaseFriendshipValue;
      private byte itemIncreasePokemonLevelValue;
      private FashionLimitType itemFashionLimitType;
      private ItemEffectType itemFirstEffectType;
      private ItemEffectType itemSecondEffectType;
      private byte specItemLimitUseTimes;
      private int itemShopBuyPrice;
      private int itemShopSellPrice;
      private short itemLimitYear;
      private byte itemLimitMonth;
      private short itemSummonPokemonIndexId;
      private List<Short> itemLimitUsePokemons = new java.util.ArrayList<>();
      private byte seedFlavorType;
      private byte seedFlavorValue;
      private short berryItemIndexId;
      private short berryGrowMinutes;
      private short berryWitheredTime;
      private byte itemBerryEffectFlag;
      private byte berryThirstyType;
      private byte berryWiltsSpeedType;
      private byte berryGrowTimeType;
      private PokemonType berryPokemonType;
      private byte berryMainFlavor;
      private List<Short> berryFlavorArray = new java.util.ArrayList<>();
      private short itemMoveIndexId;
      private short itemMoveBasePower;
      private short itemMoveIconIndexId;
      private short itemBuffId;
      private int itemExpValue;
      private byte itemEvChangeType;
      private short itemEvChangeValue;
      private byte itemHolidayLimitType;
      private byte itemFriendshipFlavorType;
      private boolean isRecoverHpPercent;
      private byte itemStoryLineItemHolidayType;
      private byte dyeMinColor;
      private byte dyeMaxColor;
      private byte itemBallType;
      private ItemCampaignLimitType itemCampaignLimitType;
      private byte itemCureStatusConditionValue;
      private ParticleEffectType itemParticleEffectType;
      private byte itemRecoverPpValue;
      private short itemRecoverHpValue;
      private List<ItemAdditionStringData> itemAdditionStringDatas = new java.util.ArrayList<>();

      public Builder setItemIndexId(short itemIndexId) {
         this.itemIndexId = itemIndexId;
         return this;
      }

      public Builder setRegionIndexId(byte regionIndexId) {
         this.regionIndexId = regionIndexId;
         return this;
      }

      public Builder setItemType(ItemType itemType) {
         this.itemType = itemType;
         return this;
      }

      public Builder setItemTypeInfoFlag(byte itemTypeInfoFlag) {
         this.itemTypeInfoFlag = itemTypeInfoFlag;
         return this;
      }

      public Builder setItemRarityType(ItemRarityType itemRarityType) {
         this.itemRarityType = itemRarityType;
         return this;
      }

      public Builder setIsItemGTLMaxPriceLimit(boolean isItemGTLMaxPriceLimit) {
         this.isItemGTLMaxPriceLimit = isItemGTLMaxPriceLimit;
         return this;
      }

      public Builder setIsBindAccount(boolean isBindAccount) {
         this.isBindAccount = isBindAccount;
         return this;
      }

      public Builder setIsTradeAble(boolean isTradeAble) {
         this.isTradeAble = isTradeAble;
         return this;
      }

      public Builder setItemCanGiveToPokemon(boolean itemCanGiveToPokemon) {
         this.itemCanGiveToPokemon = itemCanGiveToPokemon;
         return this;
      }

      public Builder setIsDestoryable(boolean isDestoryable) {
         this.isDestoryable = isDestoryable;
         return this;
      }

      public Builder setCanUseOnPokemon(boolean canUseOnPokemon) {
         this.canUseOnPokemon = canUseOnPokemon;
         return this;
      }

      public Builder setCanDestoryForever(boolean canDestoryForever) {
         this.canDestoryForever = canDestoryForever;
         return this;
      }

      public Builder setIsRomOriginalFashion(boolean isRomOriginalFashion) {
         this.isRomOriginalFashion = isRomOriginalFashion;
         return this;
      }

      public Builder setItemMaxStackSize(short itemMaxStackSize) {
         this.itemMaxStackSize = itemMaxStackSize;
         return this;
      }

      public Builder setItemIncreaseFriendshipValue(short itemIncreaseFriendshipValue) {
         this.itemIncreaseFriendshipValue = itemIncreaseFriendshipValue;
         return this;
      }

      public Builder setItemIncreasePokemonLevelValue(byte itemIncreasePokemonLevelValue) {
         this.itemIncreasePokemonLevelValue = itemIncreasePokemonLevelValue;
         return this;
      }

      public Builder setItemFashionLimitType(FashionLimitType itemFashionLimitType) {
         this.itemFashionLimitType = itemFashionLimitType;
         return this;
      }

      public Builder setItemFirstEffectType(ItemEffectType itemFirstEffectType) {
         this.itemFirstEffectType = itemFirstEffectType;
         return this;
      }

      public Builder setItemSecondEffectType(ItemEffectType itemSecondEffectType) {
         this.itemSecondEffectType = itemSecondEffectType;
         return this;
      }

      public Builder setSpecItemLimitUseTimes(byte specItemLimitUseTimes) {
         this.specItemLimitUseTimes = specItemLimitUseTimes;
         return this;
      }

      public Builder setItemShopBuyPrice(int itemShopBuyPrice) {
         this.itemShopBuyPrice = itemShopBuyPrice;
         return this;
      }

      public Builder setItemShopSellPrice(int itemShopSellPrice) {
         this.itemShopSellPrice = itemShopSellPrice;
         return this;
      }

      public Builder setItemLimitYear(short itemLimitYear) {
         this.itemLimitYear = itemLimitYear;
         return this;
      }

      public Builder setItemLimitMonth(byte itemLimitMonth) {
         this.itemLimitMonth = itemLimitMonth;
         return this;
      }

      public Builder setItemSummonPokemonIndexId(short itemSummonPokemonIndexId) {
         this.itemSummonPokemonIndexId = itemSummonPokemonIndexId;
         return this;
      }

      public Builder addItemLimitUsePokemons(short itemLimitUsePokemonIndexId) {
         this.itemLimitUsePokemons.add(itemLimitUsePokemonIndexId);
         return this;
      }

      public Builder setSeedFlavorType(byte seedFlavorType) {
         this.seedFlavorType = seedFlavorType;
         return this;
      }

      public Builder setSeedFlavorValue(byte seedFlavorValue) {
         this.seedFlavorValue = seedFlavorValue;
         return this;
      }

      public Builder setBerryItemIndexId(short berryItemIndexId) {
         this.berryItemIndexId = berryItemIndexId;
         return this;
      }

      public Builder setBerryGrowMinutes(short berryGrowMinutes) {
         this.berryGrowMinutes = berryGrowMinutes;
         return this;
      }

      public Builder setBerryWitheredTime(short berryWitheredTime) {
         this.berryWitheredTime = berryWitheredTime;
         return this;
      }

      public Builder setItemBerryEffectFlag(byte itemBerryEffectFlag) {
         this.itemBerryEffectFlag = itemBerryEffectFlag;
         return this;
      }

      public Builder setBerryThirstyType(byte berryThirstyType) {
         this.berryThirstyType = berryThirstyType;
         return this;
      }

      public Builder setBerryWiltsSpeedType(byte berryWiltsSpeedType) {
         this.berryWiltsSpeedType = berryWiltsSpeedType;
         return this;
      }

      public Builder setBerryGrowTimeType(byte berryGrowTimeType) {
         this.berryGrowTimeType = berryGrowTimeType;
         return this;
      }

      public Builder setBerryPokemonType(PokemonType berryPokemonType) {
         this.berryPokemonType = berryPokemonType;
         return this;
      }

      public Builder setBerryMainFlavor(byte berryMainFlavor) {
         this.berryMainFlavor = berryMainFlavor;
         return this;
      }

      public Builder addBerryFlavor(short berryFlavor) {
         this.berryFlavorArray.add(berryFlavor);
         return this;
      }

      public Builder setItemMoveIndexId(short itemMoveIndexId) {
         this.itemMoveIndexId = itemMoveIndexId;
         return this;
      }

      public Builder setItemMoveBasePower(short itemMoveBasePower) {
         this.itemMoveBasePower = itemMoveBasePower;
         return this;
      }

      public Builder setItemMoveIconIndexId(short itemMoveIconIndexId) {
         this.itemMoveIconIndexId = itemMoveIconIndexId;
         return this;
      }

      public Builder setItemBuffId(short itemBuffId) {
         this.itemBuffId = itemBuffId;
         return this;
      }

      public Builder setItemExpValue(int itemExpValue) {
         this.itemExpValue = itemExpValue;
         return this;
      }

      public Builder setItemEvChangeType(byte itemEvChangeType) {
         this.itemEvChangeType = itemEvChangeType;
         return this;
      }

      public Builder setItemEvChangeValue(short itemEvChangeValue) {
         this.itemEvChangeValue = itemEvChangeValue;
         return this;
      }

      public Builder setItemHolidayLimitType(byte itemHolidayLimitType) {
         this.itemHolidayLimitType = itemHolidayLimitType;
         return this;
      }

      public Builder setItemFriendshipFlavorType(byte itemFriendshipFlavorType) {
         this.itemFriendshipFlavorType = itemFriendshipFlavorType;
         return this;
      }

      public Builder setIsRecoverHpPercent(boolean isRecoverHpPercent) {
         this.isRecoverHpPercent = isRecoverHpPercent;
         return this;
      }

      public Builder setItemStoryLineItemHolidayType(byte itemStoryLineItemHolidayType) {
         this.itemStoryLineItemHolidayType = itemStoryLineItemHolidayType;
         return this;
      }

      public Builder setDyeMinColor(byte dyeMinColor) {
         this.dyeMinColor = dyeMinColor;
         return this;
      }

      public Builder setDyeMaxColor(byte dyeMaxColor) {
         this.dyeMaxColor = dyeMaxColor;
         return this;
      }

      public Builder setItemBallType(byte itemBallType) {
         this.itemBallType = itemBallType;
         return this;
      }

      public Builder setItemCampaignLimitType(ItemCampaignLimitType itemCampaignLimitType) {
         this.itemCampaignLimitType = itemCampaignLimitType;
         return this;
      }

      public Builder setItemCureStatusConditionValue(byte itemCureStatusConditionValue) {
         this.itemCureStatusConditionValue = itemCureStatusConditionValue;
         return this;
      }

      public Builder setItemParticleEffectType(ParticleEffectType itemParticleEffectType) {
         this.itemParticleEffectType = itemParticleEffectType;
         return this;
      }

      public Builder setItemRecoverPpValue(byte itemRecoverPpValue) {
         this.itemRecoverPpValue = itemRecoverPpValue;
         return this;
      }

      public Builder setItemRecoverHpValue(short itemRecoverHpValue) {
         this.itemRecoverHpValue = itemRecoverHpValue;
         return this;
      }
      public Builder addItemAdditionStringData(ItemAdditionStringData itemAdditionStringData) {
         this.itemAdditionStringDatas.add(itemAdditionStringData);
         return this;
      }

      public ItemData build() {
         return new ItemData(itemIndexId, regionIndexId, itemType, itemTypeInfoFlag, itemRarityType, isItemGTLMaxPriceLimit, isBindAccount, isTradeAble, itemCanGiveToPokemon, isDestoryable, canUseOnPokemon, canDestoryForever, isRomOriginalFashion, itemMaxStackSize, itemIncreaseFriendshipValue, itemIncreasePokemonLevelValue, itemFashionLimitType, itemFirstEffectType, itemSecondEffectType, specItemLimitUseTimes, itemShopBuyPrice, itemShopSellPrice, itemLimitYear, itemLimitMonth, itemSummonPokemonIndexId, itemLimitUsePokemons, seedFlavorType, seedFlavorValue, berryItemIndexId, berryGrowMinutes, berryWitheredTime, itemBerryEffectFlag, berryThirstyType, berryWiltsSpeedType, berryGrowTimeType, berryPokemonType, berryMainFlavor, berryFlavorArray, itemMoveIndexId, itemMoveBasePower, itemMoveIconIndexId, itemBuffId, itemExpValue, itemEvChangeType, itemEvChangeValue, itemHolidayLimitType, itemFriendshipFlavorType, isRecoverHpPercent, itemStoryLineItemHolidayType, dyeMinColor, dyeMaxColor, itemBallType, itemCampaignLimitType, itemCureStatusConditionValue, itemParticleEffectType, itemRecoverPpValue, itemRecoverHpValue, itemAdditionStringDatas);
      }
   }
}
