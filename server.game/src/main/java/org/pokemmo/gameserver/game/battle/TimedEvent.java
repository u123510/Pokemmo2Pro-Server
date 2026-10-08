package org.pokemmo.gameserver.game.battle;

import lombok.Getter;

@Getter
public class TimedEvent {
    private boolean resetTimeLimit;
    private boolean shouldUpdateTimeLimit;
    private short playerEachRoundMaxTimeLimit;
    private short teamEachRoundMaxTimeLimit;
}
