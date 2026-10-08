package org.pokemmo.gameserver.game.script;
import lombok.Getter;
@Getter
public class ReloadMapScript extends GameScript{
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;

public ReloadMapScript(byte regionIndexId, byte mapHeaderIdOrGbaMapGroupId, byte gbaMapId) {
    super(ScriptActionType.RELOAD_MAP);
    this.regionIndexId = regionIndexId;
    this.mapHeaderIdOrGbaMapGroupId = mapHeaderIdOrGbaMapGroupId;
    this.gbaMapId = gbaMapId;
    }
}
