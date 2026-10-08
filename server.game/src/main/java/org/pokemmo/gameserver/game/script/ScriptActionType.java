package org.pokemmo.gameserver.game.script;
public enum ScriptActionType {
    UPDATE_ENTITY_POS(0xF4,"UpdateEntityPos"),
    RELOAD_MAP(0xF5,"ReloadMap"),
    RIVAL_BATTLE_TRIGGER(0xF6,"RivalBattleTrigger"),
    RESET_PLAYER_LAST_RECORD(0xF7,"ResetPlayerLastRecord"),
    ENTITY_GUIDE_PLAYER_AUTO_MOVE(0xFA,"EntityGuidePlayerAutoMove"),
    ENTITY_AUTO_MOVE(0xFB,"EntityAutoMove"),
    SET_SERVER_SIDE_EVENT_STATUS(0xFC,"SetServerSideEventStatus"),
    ADD_GIFT_POKEAMON(0xFD,"AddGiftPokemon"),
    SET_GAME_SIDE_EVENT_STATUS(0xFE,"SetGameSideEventStatus"),
    CURE_PARTY_POKEMON(0xFF,"CurePartyPokemon"),
    SLEEP(0x0,"Sleep"),
    SET_ENTITY_TOWARD(0x7,"SetEntityToward"),
    REMOVE_ENTITY(0x8,"RemoveEntity"),
    UPDATE_DEX(0xB,"UpdateDex"),
    UPDATE_PLAYER_INFO(0xC,"UpdatePlayerInfo"),
    ENTITY_SPORT(0xD,"EntitySport"),
    SET_EVENT_STATUS(0xE,"SetEventStatus"),
    SET_ENTITY_POS(0x11,"SetEntityPos"),
    ADD_ENTITY(0x12,"AddEntity"),
    REMOVE_ALL_ENTITY(0x1B,"RemoveAllEntity"),
    ENTITY_INTERACTION(0x21,"EntityInteraction"),
    SHOW_POKEMON_WIDGET(0x24,"ShowPokemonWidget"),
    PLAY_MUSIC(0x25,"PlayMusic"),
    PLAY_SOUND(0x26,"PlaySound"),
    SET_FOLLOW_POKEMON(0x2B,"SetFollowPokemon"),
    BATTLE_INIT(0x30,"BattleInit"),
    VARIANT_SKIN(0x90,"VariantSkin"),
    SET_ENTITY_IDLE_MOVEMENT(0xB2,"SetEntityIdleMovement"),
    SET_RENDER_SCREEN(0xB4,"SetRenderScreen"),
    SET_GAME_FRAME(0xB6,"SetGameFrame"),
    LOAD_SEASON(0xB9,"LoadSeason"),
    BUILDING_ANIMATION(0xC0,"BuildingAnimation");
    private final byte type;
    private final String name;
    ScriptActionType(int actionType, String name) {
        this.type = (byte) actionType;
        this.name = name;
    }
    public byte getActionType() {
        return type;
    }
    public String getName(){
        return name;
    }
    public static ScriptActionType getByType(int actionType) {
        for (ScriptActionType interactionType : values()) {
            if (interactionType.getActionType() == actionType) {
                return interactionType;
            }
        }
        return null;
    }
    public static ScriptActionType getByName(String name) {
        for (ScriptActionType interactionType : values()) {
            if (interactionType.getName().equals(name)) {
                return interactionType;
            }
        }
        throw new IllegalArgumentException("未知的脚本类型名称 " + name);
    }
}
