package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class SleepScript extends GameScript {
    private int sleepTime;
    public SleepScript(int sleepTime) {
        super(ScriptActionType.SLEEP);
        this.sleepTime = sleepTime;
    }
}
