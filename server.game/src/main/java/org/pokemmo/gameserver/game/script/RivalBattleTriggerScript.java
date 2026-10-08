package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class RivalBattleTriggerScript extends GameScript{
    private byte rivalTeamId;
    public RivalBattleTriggerScript(int rivalTeamId) {
        super(ScriptActionType.RIVAL_BATTLE_TRIGGER);
        this.rivalTeamId = (byte) rivalTeamId;
    }
}
