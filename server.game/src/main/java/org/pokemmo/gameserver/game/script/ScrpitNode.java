package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class ScrpitNode {
    private final ScriptActionType interactionType;
    private final GameScript scriptObject;
    public ScriptActionType getInteractionType() {
        return this.interactionType;
    }
    public GameScript getScriptObject() {
        return this.scriptObject;
    }
    public ScrpitNode(ScriptActionType interactionType, GameScript scriptObject) {
        this.interactionType = interactionType;
        this.scriptObject = scriptObject;
    }
}
