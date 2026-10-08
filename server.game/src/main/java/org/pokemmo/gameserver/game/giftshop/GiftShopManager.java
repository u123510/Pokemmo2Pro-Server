package org.pokemmo.gameserver.game.giftshop;

import org.pokemmo.gameserver.util.JsonUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 礼品商城物品管理器，负责加载和提供商城物品列表
 */
public class GiftShopManager extends JsonUtil {
    private final Map<Integer, GiftShopItem> itemMap = new ConcurrentHashMap<>();
    private final List<GiftShopItem> itemList = new ArrayList<>();
    private String configFilePath;

    public GiftShopManager(String configFilePath) {
        this.configFilePath = configFilePath;
        loadGiftShopItems(configFilePath);
    }

    public synchronized void loadGiftShopItems(String path) {
        this.configFilePath = path;
        File file = new File(path);
        if (!file.exists()) {
            getLogger().warn("礼品商城配置文件未找到: {}，将初始化为空商城列表", path);
            itemMap.clear();
            itemList.clear();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            JsonGiftShopConfig config = getGson().fromJson(reader, JsonGiftShopConfig.class);
            itemMap.clear();
            itemList.clear();
            if (config != null && config.getItems() != null) {
                for (GiftShopItem item : config.getItems()) {
                    itemMap.put(item.getId(), item);
                    itemList.add(item);
                }
            }
            getLogger().info("成功加载 {} 个礼品商城物品", itemList.size());
        } catch (IOException e) {
            getLogger().error("加载礼品商城配置失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            getLogger().error("解析礼品商城配置失败: {}", e.getMessage(), e);
        }
    }

    public synchronized void reload() {
        if (configFilePath != null) {
            loadGiftShopItems(configFilePath);
        }
    }

    public List<GiftShopItem> getItems() {
        return Collections.unmodifiableList(itemList);
    }

    public GiftShopItem getItemById(int id) {
        return itemMap.get(id);
    }
}
