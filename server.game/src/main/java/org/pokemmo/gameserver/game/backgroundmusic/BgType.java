package org.pokemmo.gameserver.game.backgroundmusic;

public enum BgType {
    BG01(1, 0, 34, 35, false),
    BG02(2, 1, 36, 40, true),
    BG03(3, 5, 44, 45, false),
    BG04(4, 1, 46, 50, true),
    BG05(5, 6, 54, 55, false),
    BG06(6, 7, 56, 57, false),
    BG07(7, 8, 58, 59, false),
    BG08(8, 13, 60, 61, false),
    BG09(9, 14, 62, 63, false),
    BG10(10, 4, 64, 65, false),
    BG11(11, 17, 66, 67, false),
    BG12(12, 21, 70, 71, false),
    BG13(13, 22, 72, 73, false),
    BG14(14, 23, 74, 75, false),
    BG15(15, 24, 76, 77, false),
    BG16(16, 25, 78, 79, false),
    BG17(17, 27, 82, 83, false),
    BG18(18, 27, 78, 79, false),
    BG100(100, 28, 82, 35, false),
    BG101(101, 33, 76, 77, false),
    BG102(102, 31, 60, 35, false);
    private final int bgIndex;
    private final int type;
    private final int unk;
    private final int unk1;
    private final boolean unk2;
    BgType(int bgIndex, int type, int unk, int unk1, boolean unk2) {
        this.bgIndex = bgIndex;
        this.type = type;
        this.unk = unk;
        this.unk1 = unk1;
        this.unk2 = unk2;

    }
    public int getBgIndex() {
        return bgIndex;
    }
    public int getType() {
        return type;
    }
}
