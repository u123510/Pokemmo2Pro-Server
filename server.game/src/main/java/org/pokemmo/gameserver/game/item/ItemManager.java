package org.pokemmo.gameserver.game.item;
import org.pokemmo.gameserver.util.JsonUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class ItemManager extends JsonUtil {
    public static final short OAK_PARCEL_ITEM_ID = 349;
    private static final HashMap<Short, ItemData> itemDataHashMap = new HashMap<>(3873);

    /**
     * These ranges are rejected by the client trade predicate itself. The
     * Item.bin byte currently exposed as isTradeAble is not a boolean trade
     * flag, so ordinary items follow the client's default-allow behavior.
     */
    private static final int[][] CLIENT_NON_TRADEABLE_RANGES = {
            {339, 346},
            {1297, 1298},
            {5420, 5425},
            {8420, 8427},
            {9420, 9427}
    };
    public ItemManager (String itemInfoFilePath) {
        loadItemsInfo(itemInfoFilePath);
    }
    public void loadItemsInfo(String binFilePath) {
        ItemDataReader itemDataReader = new ItemDataReader(new File(binFilePath));
        try {
            List<ItemData> itemDataList = itemDataReader.readItemData();
            for (ItemData itemData : itemDataList) {
                itemDataHashMap.put(itemData.getItemIndexId(), itemData);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static ItemData getItemData(short itemIndexId){
        return itemDataHashMap.get(itemIndexId);
    }

    /**
     * Returns whether an item may be exchanged by player trade/GTL.
     *
     * The client initializes its per-item trade flag to true and only
     * overrides selected items through global configuration. ItemDataReader's
     * legacy isTradeAble field is a packed Item.bin byte, not that flag, so it
     * must not reject ordinary items such as item index 1476.
     */
    public static boolean isTradeableForExchange(short itemIndexId) {
        if (getItemData(itemIndexId) == null || isStoryBound(itemIndexId)) {
            return false;
        }
        int unsignedItemIndexId = Short.toUnsignedInt(itemIndexId);
        for (int[] range : CLIENT_NON_TRADEABLE_RANGES) {
            if (unsignedItemIndexId >= range[0] && unsignedItemIndexId <= range[1]) {
                return false;
            }
        }
        return true;
    }

    public static boolean isStoryBound(short itemIndexId) {
        return itemIndexId == OAK_PARCEL_ITEM_ID;
    }
}
