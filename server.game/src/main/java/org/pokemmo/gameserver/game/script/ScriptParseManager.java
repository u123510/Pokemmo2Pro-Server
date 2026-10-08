package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import org.pokemmo.gameserver.game.entity.SportType;
import org.pokemmo.gameserver.game.events.EventRegionType;
import org.pokemmo.gameserver.game.frame.FrameType;
import org.pokemmo.gameserver.game.interact.GameInteractionType;
import org.pokemmo.gameserver.util.JsonUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Getter
public class ScriptParseManager extends JsonUtil {
    private static File scriptFile;
    private static String interactor;
    private static boolean isNdsType;
    private static byte regionIndexId ;
    private static byte mapHeaderIdOrGbaMapGroupId ;
    private static byte gbaMapId ;
    private static short x ;
    private static short y ;
    private static byte z ;
    private static byte toward ;
    private static short pokemonIndexId;
    private static boolean isShiny;
    public ScriptParseManager(File scriptFile) {
        this.scriptFile = scriptFile;
    }
    public List<Script> parseScriptData(String jsonFilePath) {
        List<Script> scripts = null;
        try (Reader reader = new FileReader(jsonFilePath))
        {
            JsonScriptsConfig config = getGson().fromJson(reader, JsonScriptsConfig.class);
            // 转换JSON配置为Script对象
            scripts = convertToScript(config);
        } catch (IOException e) {
            getLogger().error("加载事件脚本配置失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            getLogger().error("解析事件脚本配置失败{}: {}", jsonFilePath, e.getMessage(), e);
        }
        return scripts;
    }
    private static List<Script> convertToScript(JsonScriptsConfig config) {
        List<JsonScriptConfig> scriptConfigs = config.getScripts();
        List<Script> scripts = new ArrayList<>();
        for(JsonScriptConfig scriptConfig : scriptConfigs)
        {
            Script script = new Script(scriptConfig.getName());
            // 添加所有脚本节点
            for (JsonNodeConfig nodeConfig : scriptConfig.getNodes()) {
                ScriptActionType interactionType = ScriptActionType.getByName(nodeConfig.getInteractionType());
                GameScript scriptData = createScriptData(interactionType, nodeConfig.getParameters());
                ScrpitNode node = new ScrpitNode(interactionType, scriptData);
                script.addScrpitNode(node);
            }
            // 添加跳转节点ID
            if (scriptConfig.getNextScripts() != null) {
                for (String nextNodeName : scriptConfig.getNextScripts()) {
                    script.addNextNodeId(nextNodeName);
                }
            }
            scripts.add(script);
        }
        return scripts;
    }

    /**
     * 根据交互类型创建对应的脚本数据对象
     */
    private static GameScript createScriptData(ScriptActionType interactionType, Map<String, Object> parameters) {
        switch (interactionType)
        {
            case UPDATE_ENTITY_POS:
                regionIndexId = ((Double) parameters.get("regionIndexId")).byteValue();
                mapHeaderIdOrGbaMapGroupId = ((Double) parameters.get("mapHeaderIdOrGbaMapGroupId")).byteValue();
                gbaMapId = ((Double) parameters.get("gbaMapId")).byteValue();
                x = ((Double) parameters.get("x")).shortValue();
                y = ((Double) parameters.get("y")).shortValue();
                z = ((Double) parameters.get("z")).byteValue();
                toward = ((Double) parameters.get("toward")).byteValue();
                return new UpdateEntityPosScript(regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, x, y, z, toward);
            case RELOAD_MAP:
                interactor = (String) parameters.get("interactor");
                regionIndexId = ((Double) parameters.get("regionIndexId")).byteValue();
                mapHeaderIdOrGbaMapGroupId = ((Double) parameters.get("mapHeaderIdOrGbaMapGroupId")).byteValue();
                gbaMapId = ((Double) parameters.get("gbaMapId")).byteValue();
                return new ReloadMapScript(regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId);
            case RIVAL_BATTLE_TRIGGER:
                byte rivalTeamId = ((Double) parameters.get("rivalTeamId")).byteValue();
                return new RivalBattleTriggerScript(rivalTeamId);
            case SET_SERVER_SIDE_EVENT_STATUS:
                String eventName = (String) parameters.get("eventName");
                short eventStatus = ((Double) parameters.get("eventStatus")).shortValue();
                EventRegionType eventRegionType = EventRegionType.getByType( ((Double) parameters.get("eventRegionType")).intValue());
                return new SetServerSideEventStatusScript(eventName, eventRegionType, eventStatus);
            case ADD_GIFT_POKEAMON:
                short giftId = ((Double) parameters.get("giftId")).shortValue();
                return new AddGiftPokemonScript(giftId);
            case ENTITY_GUIDE_PLAYER_AUTO_MOVE:
                interactor = (String) parameters.get("interactor");
                regionIndexId = ((Double) parameters.get("regionIndexId")).byteValue();
                mapHeaderIdOrGbaMapGroupId = ((Double) parameters.get("mapHeaderIdOrGbaMapGroupId")).byteValue();
                gbaMapId = ((Double) parameters.get("gbaMapId")).byteValue();
                isNdsType = Boolean.parseBoolean(parameters.get("isNdsType").toString());
                x = ((Double) parameters.get("x")).shortValue();
                y = ((Double) parameters.get("y")).shortValue();
                z = ((Double) parameters.get("z")).byteValue();
                return new AutoMoveScript(interactor, isNdsType, regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, x, y, z);
            case ENTITY_AUTO_MOVE:
                interactor = (String) parameters.get("interactor");
                regionIndexId = ((Double) parameters.get("regionIndexId")).byteValue();
                mapHeaderIdOrGbaMapGroupId = ((Double) parameters.get("mapHeaderIdOrGbaMapGroupId")).byteValue();
                gbaMapId = ((Double) parameters.get("gbaMapId")).byteValue();
                isNdsType = Boolean.parseBoolean(parameters.get("isNdsType").toString());
                x = ((Double) parameters.get("x")).shortValue();
                y = ((Double) parameters.get("y")).shortValue();
                z = ((Double) parameters.get("z")).byteValue();
                return new AutoMoveScript(interactor, isNdsType, regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, x, y, z);
            case CURE_PARTY_POKEMON:
                return new CurePartyPokemonScript();
            case SLEEP:
                int sleepTime = ((Double) parameters.get("sleepTime")).intValue();
                return new SleepScript(sleepTime);
            case SET_EVENT_STATUS://SET_EVENT_STATUS
                boolean hasEvent = Boolean.parseBoolean(parameters.get("hasEvent").toString());
                return new SetEventStatusScript(hasEvent);
            case SET_ENTITY_TOWARD:// SET_ENTITY_TOWARD
                interactor = (String) parameters.get("interactor");
                toward = ((Double) parameters.get("toward")).byteValue();
                return new SetEntityTowardScript(interactor, toward);
            case REMOVE_ENTITY:
                interactor = (String) parameters.get("interactor");
                return new RemoveEntityScript(interactor);
            case UPDATE_DEX:
                byte dexLevel = ((Double) parameters.get("dexLevel")).byteValue();
                pokemonIndexId = ((Double) parameters.get("pokemonIndexId")).byteValue();
                byte mask = ((Double) parameters.get("mask")).byteValue();
                return new UpdateDexScript(dexLevel, pokemonIndexId, mask);
            case UPDATE_PLAYER_INFO:
                boolean isRefreshMoney = Boolean.parseBoolean(parameters.get("isRefreshMoney").toString());
                boolean isRefreshSafariInfo = Boolean.parseBoolean(parameters.get("isRefreshSafariInfo").toString());
                boolean isRefreshCoins = Boolean.parseBoolean(parameters.get("isRefreshCoins").toString());
                boolean isRefreshBoxInfo = Boolean.parseBoolean(parameters.get("isRefreshBoxInfo").toString());
                boolean isRefreshRepelInfo = Boolean.parseBoolean(parameters.get("isRefreshRepelInfo").toString());
                boolean isRefreshBattlePoints = Boolean.parseBoolean(parameters.get("isRefreshBattlePoints").toString());
                boolean isRefreshRureInfo = Boolean.parseBoolean(parameters.get("isRefreshRureInfo").toString());
                boolean isRefreshParticleEffectInfo = Boolean.parseBoolean(parameters.get("isRefreshParticleEffectInfo").toString());
                return new UpdatePlayerInfoScript(isRefreshMoney, isRefreshSafariInfo, isRefreshCoins, isRefreshBoxInfo, isRefreshRepelInfo, isRefreshBattlePoints, isRefreshRureInfo, isRefreshParticleEffectInfo);
            case SET_ENTITY_POS:
                interactor = (String) parameters.get("interactor");
                regionIndexId = ((Double) parameters.get("regionIndexId")).byteValue();
                mapHeaderIdOrGbaMapGroupId = ((Double) parameters.get("mapHeaderIdOrGbaMapGroupId")).byteValue();
                gbaMapId = ((Double) parameters.get("gbaMapId")).byteValue();
                x = ((Double) parameters.get("x")).shortValue();
                y = ((Double) parameters.get("y")).shortValue();
                z = ((Double) parameters.get("z")).byteValue();
                toward = ((Double) parameters.get("toward")).byteValue();
                return new SetEntityPosScript(interactor, regionIndexId, mapHeaderIdOrGbaMapGroupId, gbaMapId, x, y, z, toward);
            case ADD_ENTITY:
                String enityName = parameters.get("enityName").toString();
                return new AddEntityScript(enityName);
            case ENTITY_INTERACTION:// ENTITY_INTERACTION
                interactor = (String) parameters.get("interactor");
                int gameInteractionTypeValue = ((Double) parameters.get("gameInteractionType")).intValue();
                GameInteractionType gameInteractionType = GameInteractionType.getGameInteractionType(gameInteractionTypeValue);
                int stringOffset = ((Double) parameters.get("stringOffset")).intValue();
                int delay = ((Double) parameters.get("interactDelay")).intValue();
                int localStringFormatSize = ((Double) parameters.get("localStringFormatSize")).intValue();
                if(localStringFormatSize > 0) {
                    ArrayList<?> data = (ArrayList<?>) parameters.get("data");
                    ArrayList<?> formatIndexArray = null;
                    ArrayList<Short> formatIndexIdList = new ArrayList<Short>(0);
                    ArrayList<LocalFormatStringScript> localFormatStringScriptList = new ArrayList<LocalFormatStringScript>(0);
                    byte replaceIndex = 0;
                    byte stringType = 0;
                    byte regionIndexId = 0;
                    long localStringId = 0;
                    int intStringId = 0;
                    String exportString = "";
                    for (Object item : data) {
                        Map<?, ?> dataItem = (Map<?, ?>) item;
                        if (dataItem.containsKey("replaceIndex")) {
                            replaceIndex = ((Number) dataItem.get("replaceIndex")).byteValue();
                        }
                        if (dataItem.containsKey("stingType")) {
                            stringType = ((Number) dataItem.get("stingType")).byteValue();
                        }
                        if (dataItem.containsKey("regionIndexId")) {
                            regionIndexId = ((Number) dataItem.get("regionIndexId")).byteValue();
                        }
                        if (dataItem.containsKey("localStringId")) {
                            localStringId = ((Number) dataItem.get("stingType")).longValue();
                        }
                        if(dataItem.containsKey("intStringId")){
                            intStringId = ((Number) dataItem.get("intStringId")).intValue();
                        }
                        if (dataItem.containsKey("formatIndexId")){
                            formatIndexArray = (ArrayList<?>) dataItem.get("formatIndexId");
                            if (formatIndexArray != null) {
                                for (Object index : formatIndexArray) {
                                    formatIndexIdList.add(((Number) index).shortValue());
                                }
                            }
                        }
                        if(dataItem.containsKey("exportString")){
                            exportString = dataItem.get("exportString").toString();
                        }
                        localFormatStringScriptList.add(new LocalFormatStringScript(replaceIndex,stringType,regionIndexId,localStringId,intStringId,formatIndexIdList,exportString));
                    }
                    return new InteractScript(interactor,gameInteractionType,stringOffset,delay,localStringFormatSize,localFormatStringScriptList);
                }
                return new InteractScript(interactor,gameInteractionType, stringOffset, delay, localStringFormatSize);
            case SHOW_POKEMON_WIDGET:
                pokemonIndexId = ((Double) parameters.get("pokemonIndexId")).shortValue();
                byte pokemonFormType = ((Double) parameters.get("pokemonFormType")).byteValue();
                isShiny = Boolean.parseBoolean(parameters.get("isShiny").toString());
                return new ShowPokemonWidgetScript(pokemonIndexId, pokemonFormType, isShiny);
            case ENTITY_SPORT:// ENTITY_SPORT
                interactor = (String) parameters.get("interactor");
                isNdsType = Boolean.parseBoolean(parameters.get("isNdsType").toString());
                ArrayList<?> sportTypeValues = (ArrayList<?>) parameters.get("sport");
                List<SportType> sportTypes = new ArrayList<>();
                for (Object item : sportTypeValues) {
                    sportTypes.add(SportType.getSportType(((Double) item).intValue()));
                }
                return new EntitySportScript(interactor, isNdsType, sportTypes);
            case PLAY_MUSIC:
                int musicRegionIndexId = ((Double) parameters.get("musicRegionIndexId")).intValue();
                int musicIndexId = ((Double) parameters.get("musicIndexId")).intValue();
                boolean isStopCurrentMusic = Boolean.parseBoolean(parameters.get("isStopCurrentMusic").toString());
                return new PlayMusicScript(musicRegionIndexId, musicIndexId, isStopCurrentMusic);
            case PLAY_SOUND:
                int soundRegionIndexId = ((Double) parameters.get("soundRegionIndexId")).intValue();
                int soundIndexId = ((Double) parameters.get("soundIndexId")).intValue();
                int soundType = ((Double) parameters.get("soundType")).intValue();
                return new PlaySoundScript(soundRegionIndexId, soundIndexId, soundType);
            case SET_FOLLOW_POKEMON:
                interactor = (String) parameters.get("interactor");
                pokemonIndexId = ((Double) parameters.get("pokemonIndexId")).shortValue();
                byte pokemonSex = ((Double) parameters.get("pokemonSex")).byteValue();
                isShiny = Boolean.parseBoolean(parameters.get("isShiny").toString());
                boolean isAlpha = Boolean.parseBoolean(parameters.get("isAlpha").toString());
                boolean isIngoreFllowError = Boolean.parseBoolean(parameters.get("isIngoreFllowError").toString());
                return new SetFollowPokemonScript(interactor, pokemonIndexId, pokemonSex, isShiny, isAlpha, isIngoreFllowError);
            case SET_GAME_FRAME:// SET_GAME_FRAME
                FrameType frameType = FrameType.getByType(((Double) parameters.get("frameType")).intValue());
                ArrayList<?> flashArrayValues = (ArrayList<?>) parameters.get("data");
                short[] FlashArray = new short[flashArrayValues.size()];
                for (int i = 0; i < flashArrayValues.size(); i++) {
                    FlashArray[i] = ((Double) flashArrayValues.get(i)).shortValue();
                }
                return new SetGameFrameScript(frameType, FlashArray);
            case BUILDING_ANIMATION:
                int buildingIndexId = ((Double) parameters.get("buildingIndexId")).intValue();
                int doorIndexIdd = ((Double) parameters.get("doorIndexId")).intValue();
                boolean isOpen = Boolean.parseBoolean(parameters.get("isOpen").toString());
                return new BuildingAnimationScript(buildingIndexId, doorIndexIdd, isOpen);
            case SET_ENTITY_IDLE_MOVEMENT:
                interactor = (String) parameters.get("interactor");
                int idleMovementType = ((Double) parameters.get("idleMovementType")).intValue();
                return new SetEntityIdleMovementScript(interactor, idleMovementType);
            default:
                throw new IllegalArgumentException("不支持的交互类型: " + interactionType);
        }
    }
}
