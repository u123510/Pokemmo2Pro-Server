package org.pokemmo.gameserver.game.script;

public class ScreenFlashScript {
    private byte flashType;
    private byte InteractTimes;
    private short[] flashTimes;
     public ScreenFlashScript(byte flashType, byte InteractTimes, short[] flashTimes) {
        this.flashType = flashType;
        this.InteractTimes = InteractTimes;
        this.flashTimes = flashTimes;
    }
     public byte getFlashType() {
        return flashType;
    }
    public byte getInteractTimes() {
        return InteractTimes;
    }
    public short[] getFlashTimes() {
        return flashTimes;
    }
}
