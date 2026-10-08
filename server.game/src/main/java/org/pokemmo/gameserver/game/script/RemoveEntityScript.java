package org.pokemmo.gameserver.game.script;
import lombok.Getter;
@Getter
public class RemoveEntityScript extends GameScript {
    private String removeEntityInteractor;
    public RemoveEntityScript(String removeEntityInteractor) {
        super(ScriptActionType.REMOVE_ENTITY);
        this.removeEntityInteractor = removeEntityInteractor;
    }
}
