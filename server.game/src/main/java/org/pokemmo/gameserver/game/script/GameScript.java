package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class GameScript {
    private ScriptActionType scriptActionType;
    public GameScript(ScriptActionType scriptActionType) {
        this.scriptActionType = scriptActionType;
    }
}
