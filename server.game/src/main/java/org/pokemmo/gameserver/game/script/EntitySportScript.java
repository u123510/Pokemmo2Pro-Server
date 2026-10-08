package org.pokemmo.gameserver.game.script;

import lombok.Getter;
import org.pokemmo.gameserver.game.entity.SportType;

import java.util.List;

@Getter
public class EntitySportScript extends GameScript {
    private String interactor;
    private boolean isNdsType;
    private List<SportType> sportTypes;
    public EntitySportScript(String interactor, boolean isNdsType, List<SportType> sportTypes) {
        super(ScriptActionType.ENTITY_SPORT);
        this.interactor = interactor;
        this.isNdsType = isNdsType;
        this.sportTypes = sportTypes;
    }
}
