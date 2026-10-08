package org.pokemmo.gameserver.game.map;

public class MapConnection {
    private MapConnectionType type;
    private int connectOffset;
    private byte connectMapHeaderIdOrGbaMapGroup;
    private byte connectGbaMapId;
    MapConnection(MapConnectionType type,int connectOffset,int connectMapHeaderIdOrGbaMapGroup,int connectGbaMapId){
        this.type = type;
        this.connectOffset = connectOffset;
        this.connectMapHeaderIdOrGbaMapGroup = (byte)connectMapHeaderIdOrGbaMapGroup;
        this.connectGbaMapId = (byte)connectGbaMapId;
    }
    public MapConnectionType getMapConnectionType() {
        return type;
    }
    public int getConnectOffset() {
        return connectOffset;
    }
    public byte getConnectMapHeaderIdOrGbaMapGroup() {
        return connectMapHeaderIdOrGbaMapGroup;
    }
    public byte getConnectGbaMapId() {
        return connectGbaMapId;
    }
}
