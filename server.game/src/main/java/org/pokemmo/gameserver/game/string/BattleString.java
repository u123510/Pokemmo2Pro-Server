package org.pokemmo.gameserver.game.string;

import lombok.Getter;

@Getter
public class BattleString {
    private BattleStringType battleStringType;
    //case 0
    private int stringIndexId;
    private GameLocalFormatString[] gameLocalStrings;
    //case 1
    private byte unkbyte;
    private short[] unkshortArray;
    //case 2
    private byte unkbyte2;
    private byte unkbyte3;
    private short unkshort;
    //case 3
    private byte unkbyte4;
    private LoacalStringType localStringType;
    private short unkshort2;
    private short unkshort3;

    public BattleString(BattleStringType battleStringType) {
        this.battleStringType = battleStringType;
    }
    //case 0 type
    public BattleString(int stringIndexId, GameLocalFormatString[] gameLocalStrings) {
        this.battleStringType = BattleStringType.LOCAL_STRING_TYPE;
        this.stringIndexId = stringIndexId;
        this.gameLocalStrings = gameLocalStrings;
    }
    //case 1 type
    public BattleString(int unkbyte, short[] unkshortArray) {
        this.battleStringType = BattleStringType.UNKNOWN_TYPE;
        this.unkbyte = (byte) unkbyte;
        this.unkshortArray = unkshortArray;
    }
    //case 2 type
    public BattleString(int unkbyte2, int unkbyte3, int unkshort) {
        this.battleStringType = BattleStringType.UNKNOWN_TYPE_2;
        this.unkbyte2 = (byte) unkbyte2;
        this.unkbyte3 = (byte) unkbyte3;
        this.unkshort = (short) unkshort;
    }
    //case 3 type
    public BattleString(int unkbyte4, LoacalStringType localStringType, int unkshort2, int unkshort3) {
        this.battleStringType = BattleStringType.UNKNOWN_TYPE_3;
        this.unkbyte4 = (byte) unkbyte4;
        this.localStringType = localStringType;
        this.unkshort2 = (short) unkshort2;
        this.unkshort3 = (short) unkshort3;
    }
}
