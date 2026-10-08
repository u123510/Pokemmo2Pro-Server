package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import org.pokemmo.gameserver.game.events.EventRegionType;
@Getter
public class SetServerSideEventStatusScript extends GameScript{
    private String eventName;
    private EventRegionType eventRegionType;
    private short eventStatus;
    public SetServerSideEventStatusScript(String eventName,EventRegionType eventRegionType, int eventStatus) {
        super(ScriptActionType.SET_SERVER_SIDE_EVENT_STATUS);
        this.eventName = eventName;
        this.eventRegionType = eventRegionType;
        this.eventStatus = (short) eventStatus;
    }
}
