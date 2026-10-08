package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class AutoMoveScript extends GameScript{
    private String interactor;
    private boolean isRun;
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;
    private short x;
    private short y;
    private byte z;
    public AutoMoveScript(String interactor, boolean isRun, int regionIndexId, int mapHeaderIdOrGbaMapGroupId, int gbaMapId, int x, int y, int z) {
        super(ScriptActionType.ENTITY_AUTO_MOVE);
        this.interactor = interactor;
        this.isRun = isRun;
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGbaMapGroupId = (byte) mapHeaderIdOrGbaMapGroupId;
        this.gbaMapId = (byte) gbaMapId;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
    }
}
