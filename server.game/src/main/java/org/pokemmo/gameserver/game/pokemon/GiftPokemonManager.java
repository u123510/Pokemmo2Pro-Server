package org.pokemmo.gameserver.game.pokemon;

import org.pokemmo.gameserver.util.JsonUtil;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

public class GiftPokemonManager extends JsonUtil {
    private final HashMap<Short, GiftPokemonInfo> giftPokemonInfos = new HashMap<>(900);
    public GiftPokemonManager (String giftPokemonInfoFilePath) {
        loadGiftPokemonsInfo(giftPokemonInfoFilePath);
    }
    public void loadGiftPokemonsInfo(String jsonFilePath) {
        try (Reader reader = new FileReader(jsonFilePath))
        {
            JsonGiftPokemonInfoConfigs config = getGson().fromJson(reader, JsonGiftPokemonInfoConfigs.class);
            // 转换JSON配置为Script对象
            for (JsonGiftPokemonInfoConfig giftPokemonInfoConfig : config.getGiftPokemonInfoConfigs()) {
                giftPokemonInfos.put(giftPokemonInfoConfig.getGiftId(), giftPokemonInfoConfig.toGiftPokemonInfo());
            }
            getLogger().info("成功加载 {} 个礼物宝可梦信息", giftPokemonInfos.size());
        } catch (IOException e) {
            getLogger().error("加载礼物宝可梦配置失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            getLogger().error("解析礼物宝可梦配置失败: {}", e.getMessage(), e);
        }
    }
    public GiftPokemonInfo getGiftPokemonInfoByGiftId(short giftId) {
        return giftPokemonInfos.get(giftId);
    }

}
