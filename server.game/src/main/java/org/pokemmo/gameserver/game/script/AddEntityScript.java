package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class AddEntityScript extends GameScript{
    private String entityName;
    public AddEntityScript(String entityName) {
        super(ScriptActionType.ADD_ENTITY);
        this.entityName = entityName;
    }
}
