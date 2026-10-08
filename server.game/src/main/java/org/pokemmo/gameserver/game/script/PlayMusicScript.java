package org.pokemmo.gameserver.game.script;

import lombok.Getter;

@Getter
public class PlayMusicScript extends GameScript {
    private byte musicRegionIndexId;
    private short musicIndexId;
    private boolean isStopCurrentMusic;
    public PlayMusicScript(int musicRegionIndexId, int musicIndexId, boolean isStopCurrentMusic) {
        super(ScriptActionType.PLAY_MUSIC);
        this.musicRegionIndexId = (byte) musicRegionIndexId;
        this.musicIndexId = (short) musicIndexId;
        this.isStopCurrentMusic = isStopCurrentMusic;
    }
}
