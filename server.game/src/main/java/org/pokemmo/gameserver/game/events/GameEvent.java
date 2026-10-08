package org.pokemmo.gameserver.game.events;

import lombok.Getter;

@Getter
public class GameEvent {
    private GameEventType eventType;
    private byte eventStatus;
    public GameEvent(GameEventType eventType, int eventStatus) {
        this.eventType = eventType;
        this.eventStatus = (byte) eventStatus;
    }
}
