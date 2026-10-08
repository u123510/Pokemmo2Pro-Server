package org.pokemmo.gameserver.game.script;

import java.util.ArrayList;

public class LocalFormatStringScript {
    private byte replaceIndex;
    private byte stringType;
    private byte regionIndexId;
    private long localStringId;
    private int intStringId;
    private ArrayList<Short> formatIndexIdList;
    private String exportString;
    public LocalFormatStringScript(int replaceIndex,int stringType,int regionIndexId,long localStringId,int intStringId,ArrayList<Short> formatIndexIdList,String exportString){
        this.replaceIndex= (byte) replaceIndex;
        this.stringType = (byte)stringType;
        this.regionIndexId = (byte) regionIndexId;
        this.localStringId = localStringId;
        this.intStringId = intStringId;
        this.formatIndexIdList = formatIndexIdList;
        this.exportString = exportString;
    }
    public byte getRegionIndexId() {
        return regionIndexId;
    }
    public byte getReplaceIndex() {
        return replaceIndex;
    }

    public byte getStringType() {
        return stringType;
    }

    public long getLocalStringId() {
        return localStringId;
    }

    public int getIntStringId() {
        return intStringId;
    }

    public ArrayList<Short> getFormatIndexIdList() {
        return formatIndexIdList;
    }

    public String getExportString() {
        return exportString;
    }


}
