package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class SetGameSideEventStatusScript extends GameScript{
    private long characterId;
    private String eventName;
    private short eventStatus;
    public SetGameSideEventStatusScript(long characterId, String eventName, int eventStatus) {
        super(ScriptActionType.SET_GAME_SIDE_EVENT_STATUS);
        this.characterId = characterId;
        this.eventName = eventName;
        this.eventStatus = (short) eventStatus;
    }
}
