package org.pokemmo.gameserver.script;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.pokemmo.gameserver.game.giftshop.GiftShopManager;
import org.pokemmo.gameserver.game.item.CaptureBallRateManager;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.item.ItemUseManager;
import org.pokemmo.gameserver.game.shop.ShopCatalog;
import org.pokemmo.gameserver.game.npc.CustomNpcCatalog;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.map.MapFile;
import org.pokemmo.gameserver.game.map.MapHashUtils;
import org.pokemmo.gameserver.game.map.WildEncounterManager;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.pokemon.GiftPokemonManager;
import org.pokemmo.gameserver.game.pokemon.CaptureSpeciesDataManager;
import org.pokemmo.gameserver.game.pokemon.PokemonManager;
import org.pokemmo.gameserver.game.region.RegionData;
import org.pokemmo.gameserver.game.region.RegionType;
import org.pokemmo.gameserver.game.trainer.TrainerTeamManager;
import org.pokemmo.gameserver.game.story.PalletStoryCatalog;
import org.pokemmo.gameserver.game.story.OakParcelCatalog;
import org.pokemmo.gameserver.game.story.StoryCatalog;
import org.pokemmo.gameserver.game.story.ViridianCatchCatalog;
import org.pokemmo.gameserver.game.item.KantoItemCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Setter @Getter @Log
public class ScriptManager {
    //5地区的地图数据
    private final RegionData[] regionDatas = new RegionData[5];
    private PalletStoryCatalog palletStory;
    private OakParcelCatalog oakParcelStory;
    private ViridianCatchCatalog viridianCatchStory;
    private StoryCatalog storyCatalog;
    //宝可梦信息管理器
    private PokemonManager pokemonManager;
    //道具信息管理器
    private ItemManager itemManager;
    // Server-side item behavior overrides; Item.bin remains the source of base metadata.
    private ItemUseManager itemUseManager;
    private ShopCatalog shopCatalog;
    private CustomNpcCatalog customNpcCatalog;
    // Wild-capture probability overrides keyed by item or ball type.
    private CaptureBallRateManager captureBallRateManager;
    // Generation V species catch rates and base friendship.
    private CaptureSpeciesDataManager captureSpeciesDataManager;
    //礼物宝可梦信息管理器
    private GiftPokemonManager giftPokemonManager;
    //礼品商城物品管理器
    private GiftShopManager giftShopManager;
    //劲敌队伍管理器
    private TrainerTeamManager trainerTeamManager;
    //技能信息管理器
    private MoveManager moveManager;
    //FireRed 原始野外遭遇表与物种常量
    private WildEncounterManager wildEncounterManager;
    private KantoItemCatalog kantoItemCatalog;
     public ScriptManager(String[] paths){
         Path shopDirectory = null;
         Path customNpcDirectory = null;
         for(String path:paths){
             if(path.endsWith("\\map")){
                 loadRegionMapsData(path);
                 customNpcDirectory = Path.of(path).toAbsolutePath().normalize().getParent().resolve("npc").resolve("custom");
            }else if(path.endsWith("\\pokemon")){
                //主图鉴 Pokemon.jsonc + 自定义宝可梦目录：pokemon 下子文件夹（如 gen1、gen7）中
                //每个 *.json 为一只宝可梦（文件名建议用中文名），新增精灵只往子文件夹加文件，不改动主文件
                pokemonManager = new PokemonManager(path + "\\Pokemon.jsonc", path);
                captureSpeciesDataManager = new CaptureSpeciesDataManager(path + "\\CaptureSpecies.jsonc");
             }else if(path.endsWith("\\item")){
                itemManager = new ItemManager(path + "\\Item.bin");
                Path itemDirectory = Path.of(path).toAbsolutePath().normalize();
                Path resourceDirectory = itemDirectory.getParent();
                shopDirectory = resourceDirectory == null
                        ? Path.of("resource", "shop").toAbsolutePath().normalize()
                        : resourceDirectory.resolve("shop").normalize();
                itemUseManager = new ItemUseManager(path + "\\ItemUse.jsonc");
                captureBallRateManager = new CaptureBallRateManager(path + "\\CaptureBall.jsonc");
                kantoItemCatalog = new KantoItemCatalog(
                        Path.of(path).toAbsolutePath().normalize().resolve("KantoItem.jsonc"));
             }else if(path.endsWith("\\gift") || path.endsWith("/gift")){
                 giftPokemonManager = new GiftPokemonManager(path + "\\Gift.jsonc");
                 giftShopManager = new GiftShopManager(path + "\\GiftShop.jsonc");
             }else if(path.endsWith("\\trainer")){
                trainerTeamManager = new TrainerTeamManager(path + "\\Trainer.jsonc");
                bindTrainerNpcs();
             }else if(path.endsWith("\\move")){
                 //主技能文件 Move.bin + 自定义技能目录：move 下子文件夹（如 gen6、gen9）中
                 //每个 *.json 为一个技能（文件名建议用中文名），新增技能只往子文件夹加文件，不改动主文件
                 moveManager = new MoveManager(path + "\\Move.bin", path);
             }else if(path.endsWith("\\encounter") || path.endsWith("/encounter")){
                 wildEncounterManager = new WildEncounterManager(
                         path,
                         path + "\\species.h"
                 );
             }
         }
         List<MapData> shopMaps = new ArrayList<>();
         for (RegionData region : regionDatas) {
             if (region != null) shopMaps.addAll(region.getRegionMaps().values());
         }
         if (customNpcDirectory != null) {
             customNpcCatalog = new CustomNpcCatalog(customNpcDirectory, shopMaps);
         }
         if (shopDirectory != null) {
             shopCatalog = new ShopCatalog(shopDirectory, shopMaps);
         }
        if (customNpcDirectory != null) {
            Path storyDirectory = customNpcDirectory.getParent().getParent().resolve("story");
            storyCatalog = new StoryCatalog(storyDirectory);
            palletStory = new PalletStoryCatalog(storyDirectory);
            oakParcelStory = new OakParcelCatalog(storyDirectory);
            viridianCatchStory = new ViridianCatchCatalog(storyDirectory);
        }
     }
    private void loadRegionMapsData(String path) {
         if (path == null) {
             log.severe("无法找到地图路径: " + path);
             return;
         }
         Path mapDir = Paths.get(path);
         if (!Files.exists(mapDir) || !Files.isDirectory(mapDir)) {
             log.severe("地图路径不存在或不是目录: " + path);
             return;
         }
        try (Stream<Path> regionPaths = Files.list(mapDir)) {
            regionPaths.filter(Files::isDirectory).forEach(regionPath -> {
                RegionType regionType = RegionType.getByName(
                        regionPath.getFileName().toString()
                );
                if (regionType != RegionType.KANTO
                        && regionType != RegionType.HOENN
                        && regionType != RegionType.SINNOH) {
                    return;
                }
                RegionData regionData = new RegionData(regionType);
                regionDatas[regionType.getType()] = regionData;
                try (Stream<Path> mapFiles = Files.walk(regionPath)) {
                    mapFiles.filter(Files::isRegularFile)
                            .filter(file -> file.getFileName().toString().endsWith(".json"))
                            .filter(file -> !file.getFileName().toString()
                                    .equalsIgnoreCase("Wild_Encounters.json"))
                            .sorted()
                            .forEach(file -> {
                                MapFile mapFile = new MapFile();
                                mapFile.setJsonFile(file.toFile());
                                MapData mapData = mapFile.parseMapData();
                                if (mapData != null) {
                                    regionData.getRegionMaps().put(
                                            MapHashUtils.getHash(
                                                    mapData.getRegionIndexId(),
                                                    mapData.getMapHeaderIdOrGBAmapGroupId(),
                                                    mapData.getGbaMapId()
                                            ),
                                            mapData
                                    );
                                }
                            });
                } catch (IOException exception) {
                    throw new RuntimeException("读取地图目录失败: " + regionPath, exception);
                }
                log.info(String.format(
                        "成功加载 %s 地图: %d 张",
                        regionType.getName(),
                        regionData.getRegionMaps().size()
                ));
            });
        } catch (IOException exception) {
            log.severe("读取地图根目录失败: " + mapDir + ", " + exception.getMessage());
        }
    }

    private void bindTrainerNpcs() {
        if (trainerTeamManager == null) return;
        for (RegionData region : regionDatas) {
            if (region == null) continue;
            for (MapData map : region.getRegionMaps().values()) {
                for (var npc : map.getNpcEntityHashMap().values()) {
                    var binding = trainerTeamManager.getTrainerNpcBindingByScript(
                            npc.getInteractionScriptName());
                    if (binding != null) {
                        npc.setIsTrainer(true, binding.trainerTeamId(),
                                binding.sightRange(), false);
                    }
                }
            }
        }
    }
}
