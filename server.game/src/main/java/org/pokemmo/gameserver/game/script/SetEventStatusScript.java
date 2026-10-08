package org.pokemmo.gameserver.game.script;

public class SetEventStatusScript extends GameScript{
    private boolean hasEvent;
    public SetEventStatusScript(boolean hasEvent) {
        super(ScriptActionType.SET_EVENT_STATUS);
        this.hasEvent = hasEvent;
    }
    public boolean getHasEvent() {
        return hasEvent;
    }
}
