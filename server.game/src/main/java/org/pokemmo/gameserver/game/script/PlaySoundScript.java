package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class PlaySoundScript extends GameScript {
    private byte soundRegionIndexId;
    private short soundIndexId;
    private byte soundType;
    public PlaySoundScript(int soundRegionIndexId, int soundIndexId, int soundType) {
        super(ScriptActionType.PLAY_SOUND);
        this.soundRegionIndexId = (byte) soundRegionIndexId;
        this.soundIndexId = (short) soundIndexId;
        this.soundType = (byte) soundType;
    }
}
