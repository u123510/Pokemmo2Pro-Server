package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class SetEntityTowardScript extends GameScript {
    private String interactor;
    private byte toward;
    public SetEntityTowardScript(String interactor, int toward) {
        super(ScriptActionType.SET_ENTITY_TOWARD);
        this.interactor = interactor;
        this.toward = (byte) toward;
    }
}
