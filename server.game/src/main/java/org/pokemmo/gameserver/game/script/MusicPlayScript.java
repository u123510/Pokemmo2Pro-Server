package org.pokemmo.gameserver.game.script;

public class MusicPlayScript {
    private byte musicRegionIndexId;
    private short musicIndexId;
    private boolean isPlay;
    public MusicPlayScript(int musicRegionIndexId, int musicIndexId, boolean isPlay) {
        this.musicRegionIndexId = (byte) musicRegionIndexId;
        this.musicIndexId = (short) musicIndexId;
        this.isPlay = isPlay;
    }
    public byte getMusicRegionIndexId() {
        return musicRegionIndexId;
    }
    public short getMusicIndexId() {
        return musicIndexId;
    }
    public boolean isPlay() {
        return isPlay;
    }
}
