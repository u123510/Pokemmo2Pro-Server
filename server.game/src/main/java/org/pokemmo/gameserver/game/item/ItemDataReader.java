package org.pokemmo.gameserver.game.item;
import lombok.AllArgsConstructor;
import org.pokemmo.gameserver.game.bin.BinFileReader;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
@AllArgsConstructor
public class ItemDataReader extends BinFileReader {
    private File file;
    public List<ItemData> readItemData() throws IOException {
        List<ItemData> itemDatas = new java.util.ArrayList<>(3873);
        try (FileInputStream fis = new FileInputStream(file)) {
            // 获取技能数量
            short size = readShortLE(fis);
            for(int i = 0;i<size;i++) {
                ItemData.Builder builder = new ItemData.Builder();
                short itemIndexId = readShortLE(fis);
                builder.setItemIndexId(itemIndexId);
                byte regionIndexId = readByte(fis);
                builder.setRegionIndexId(regionIndexId);
                ItemType itemType = ItemType.getByType(readByte(fis));
                builder.setItemType(itemType);
                byte itemTypeInfoFlag = readByte(fis);
                builder.setItemTypeInfoFlag(itemTypeInfoFlag);
                ItemRarityType itemRarityType = ItemRarityType.getByType(readByte(fis));
                builder.setItemRarityType(itemRarityType);
                boolean isItemGTLMaxPriceLimit = readBoolean(fis);
                builder.setIsItemGTLMaxPriceLimit(isItemGTLMaxPriceLimit);
                boolean isBindAccount = readBoolean(fis);
                builder.setIsBindAccount(isBindAccount);
                boolean isTradeAble = readBoolean(fis);
                builder.setIsTradeAble(isTradeAble);
                boolean itemCanGiveToPokemon = readBoolean(fis);
                builder.setItemCanGiveToPokemon(itemCanGiveToPokemon);
                boolean isDestoryable = readBoolean(fis);
                builder.setIsDestoryable(isDestoryable);
                boolean canUseOnPokemon = readBoolean(fis);
                builder.setCanUseOnPokemon(canUseOnPokemon);
                boolean canDestoryForever = readBoolean(fis);
                builder.setCanDestoryForever(canDestoryForever);
                boolean isRomOriginalFashion = readBoolean(fis);
                builder.setIsRomOriginalFashion(isRomOriginalFashion);
                short itemMaxStackSize = readShortLE(fis);
                builder.setItemMaxStackSize(itemMaxStackSize);
                short itemIncreaseFriendshipValue = readShortLE(fis);
                builder.setItemIncreaseFriendshipValue(itemIncreaseFriendshipValue);
                byte itemIncreasePokemonLevelValue = readByte(fis);
                builder.setItemIncreasePokemonLevelValue(itemIncreasePokemonLevelValue);
                FashionLimitType itemFashionLimitType = FashionLimitType.getByType(readByte(fis));
                builder.setItemFashionLimitType(itemFashionLimitType);
                ItemEffectType itemFirstEffectType = ItemEffectType.getByType(readByte(fis));
                builder.setItemFirstEffectType(itemFirstEffectType);
                ItemEffectType itemSecondEffectType = ItemEffectType.getByType(readByte(fis));
                builder.setItemSecondEffectType(itemSecondEffectType);
                byte specItemLimitUseTimes = readByte(fis);
                builder.setSpecItemLimitUseTimes(specItemLimitUseTimes);
                int itemShopBuyPrice = readIntLE(fis);
                builder.setItemShopBuyPrice(itemShopBuyPrice);
                int itemShopSellPrice = readIntLE(fis);
                builder.setItemShopSellPrice(itemShopSellPrice);
                short itemLimitYear = readShortLE(fis);
                builder.setItemLimitYear(itemLimitYear);
                byte itemLimitMonth = readByte(fis);
                builder.setItemLimitMonth(itemLimitMonth);
                short itemSummonPokemonIndexId = readShortLE(fis);
                builder.setItemSummonPokemonIndexId(itemSummonPokemonIndexId);
                short itemLimitUsePokemonSize = readShortLE(fis);
                for(int j = 0;j<itemLimitUsePokemonSize;j++) {
                    builder.addItemLimitUsePokemons(readShortLE(fis));
                }
                byte seedFlavorType = readByte(fis);
                builder.setSeedFlavorType(seedFlavorType);
                byte seedFlavorValue = readByte(fis);
                builder.setSeedFlavorValue(seedFlavorValue);
                short berryItemIndexId = readShortLE(fis);
                builder.setBerryItemIndexId(berryItemIndexId);
                short berryGrowMinutes = readShortLE(fis);
                builder.setBerryGrowMinutes(berryGrowMinutes);
                short berryWitheredTime = readShortLE(fis);
                builder.setBerryWitheredTime(berryWitheredTime);
                byte itemBerryEffectFlag = readByte(fis);
                builder.setItemBerryEffectFlag(itemBerryEffectFlag);
                byte berryThirstyType = readByte(fis);
                builder.setBerryThirstyType(berryThirstyType);
                byte berryWiltsSpeedType = readByte(fis);
                builder.setBerryWiltsSpeedType(berryWiltsSpeedType);
                byte berryGrowTimeType = readByte(fis);
                builder.setBerryGrowTimeType(berryGrowTimeType);
                byte berryPokemonTypeValue = readByte(fis);
                PokemonType berryPokemonType = PokemonType.getByType(berryPokemonTypeValue);
                builder.setBerryPokemonType(berryPokemonType);
                byte berryMainFlavor = readByte(fis);
                builder.setBerryMainFlavor(berryMainFlavor);
                short berryFlavorSize = readShortLE(fis);
                for(int j = 0;j<berryFlavorSize;j++) {
                    builder.addBerryFlavor(readShortLE(fis));
                }
                short itemMoveIndexId = readShortLE(fis);
                builder.setItemMoveIndexId(itemMoveIndexId);
                short itemMoveBasePower = readShortLE(fis);
                builder.setItemMoveBasePower(itemMoveBasePower);
                short itemMoveIconIndexId = readShortLE(fis);
                builder.setItemMoveIconIndexId(itemMoveIconIndexId);
                short itemBuffId = readShortLE(fis);
                builder.setItemBuffId(itemBuffId);
                int itemExpValue = readIntLE(fis);
                builder.setItemExpValue(itemExpValue);
                byte itemEvChangeType = readByte(fis);
                builder.setItemEvChangeType(itemEvChangeType);
                short itemEvChangeValue = readShortLE(fis);
                builder.setItemEvChangeValue(itemEvChangeValue);
                byte itemHolidayLimitType = readByte(fis);
                builder.setItemHolidayLimitType(itemHolidayLimitType);
                byte itemFriendshipFlavorType = readByte(fis);
                builder.setItemFriendshipFlavorType(itemFriendshipFlavorType);
                boolean isRecoverHpPercent = readBoolean(fis);
                builder.setIsRecoverHpPercent(isRecoverHpPercent);
                byte itemStoryLineItemHolidayType = readByte(fis);
                builder.setItemStoryLineItemHolidayType(itemStoryLineItemHolidayType);
                byte dyeMinColor = readByte(fis);
                builder.setDyeMinColor(dyeMinColor);
                byte dyeMaxColor = readByte(fis);
                builder.setDyeMaxColor(dyeMaxColor);
                byte itemBallType = readByte(fis);
                builder.setItemBallType(itemBallType);
                ItemCampaignLimitType itemCampaignLimitType = ItemCampaignLimitType.getByType(readByte(fis));
                builder.setItemCampaignLimitType(itemCampaignLimitType);
                byte itemCureStatusConditionValue = readByte(fis);
                builder.setItemCureStatusConditionValue(itemCureStatusConditionValue);
                ParticleEffectType itemParticleEffectType = ParticleEffectType.getByType(readByte(fis));
                builder.setItemParticleEffectType(itemParticleEffectType);
                byte itemRecoverPpValue = readByte(fis);
                builder.setItemRecoverPpValue(itemRecoverPpValue);
                short itemRecoverHpValue = readShortLE(fis);
                builder.setItemRecoverHpValue(itemRecoverHpValue);
                short itemAdditionStringDatasSize = readShortLE(fis);
                for(int j = 0;j<itemAdditionStringDatasSize;j++) {
                    int additionStringIndexId = readIntLE(fis);
                    short stringFormatDatasSize = readShortLE(fis);
                    List<ItemAdditionStringFormatData> itemAdditionStringFormatDatas = new java.util.ArrayList<>(stringFormatDatasSize);
                    for(int k = 0;k<stringFormatDatasSize;k++) {
                        ItemAdditionStringFormatData itemAdditionStringFormatData = new ItemAdditionStringFormatData();
                        itemAdditionStringFormatData.setReplaceIndex(readByte(fis));
                        itemAdditionStringFormatData.setStringType(readByte(fis));
                        short stringFormatValueSize = readShortLE(fis);
                        for(int l = 0;l<stringFormatValueSize;l++) {
                            itemAdditionStringFormatData.getStringFormatDatas().add(readShortLE(fis));
                        }
                        itemAdditionStringFormatDatas.add(itemAdditionStringFormatData);
                    }
                    ItemAdditionStringData itemAdditionStringData = new ItemAdditionStringData(additionStringIndexId,itemAdditionStringFormatDatas);
                    builder.addItemAdditionStringData(itemAdditionStringData);
                }
                ItemData item = builder.build();
                itemDatas.add(item);
            }
        }
        /*Collections.sort(itemDatas, Comparator.comparingInt(ItemData::getItemIndexId));
        exportToJson(itemDatas, new File("item.json"));*/
        return itemDatas;
    }
    /*public void exportToJson(List<ItemData> items, File outputFile) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(items, writer);
        }
    }*/
}
