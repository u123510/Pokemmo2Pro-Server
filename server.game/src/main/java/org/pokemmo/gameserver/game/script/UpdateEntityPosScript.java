package org.pokemmo.gameserver.game.script;
import lombok.Getter;
@Getter
public class UpdateEntityPosScript extends GameScript{
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;
    private short x;
    private short y;
    private byte z;
    private byte toward;
    public UpdateEntityPosScript(byte regionIndexId, byte mapHeaderIdOrGbaMapGroupId, byte gbaMapId, short x, short y, byte z, byte toward) {
        super(ScriptActionType.UPDATE_ENTITY_POS);
        this.regionIndexId = regionIndexId;
        this.mapHeaderIdOrGbaMapGroupId = mapHeaderIdOrGbaMapGroupId;
        this.gbaMapId = gbaMapId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.toward = toward;
    }
}
