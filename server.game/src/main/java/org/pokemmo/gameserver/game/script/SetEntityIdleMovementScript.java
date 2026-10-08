package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class SetEntityIdleMovementScript extends GameScript {
    private String interactor;
    private byte idleMovementType;
    public SetEntityIdleMovementScript(String interactor, int idleMovementType) {
        super(ScriptActionType.SET_ENTITY_IDLE_MOVEMENT);
        this.interactor = interactor;
        this.idleMovementType = (byte) idleMovementType;
    }
}
