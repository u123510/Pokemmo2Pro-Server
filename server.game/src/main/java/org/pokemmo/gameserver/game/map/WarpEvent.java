package org.pokemmo.gameserver.game.map;

import lombok.Getter;

@Getter
public class WarpEvent {
    private String warpName;
    private short x;
    private short y;
    private byte z;
    private byte toward;
    private byte destRegionIndexId;
    private byte destMapHeaderIdOrGbaMapGroupId;
    private byte destGbaMapId;
    private short targetX;
    private short targetY;
    private byte targetZ;
    private byte targetToward;
    public WarpEvent(String warpName,int x, int y, int z, int toward, int destRegionIndexId, int destMapHeaderIdOrGbaMapGroupId, int destGbaMapId, int targetX, int targetY, int targetZ, int targetToward) {
        this.warpName = warpName;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
        this.toward = (byte) toward;
        this.destRegionIndexId = (byte) destRegionIndexId;
        this.destMapHeaderIdOrGbaMapGroupId = (byte) destMapHeaderIdOrGbaMapGroupId;
        this.destGbaMapId = (byte) destGbaMapId;
        this.targetX = (short) targetX;
        this.targetY = (short) targetY;
        this.targetZ = (byte) targetZ;
        this.targetToward = (byte) targetToward;
    }
    //比较是否处于等效WarpEvent点
    public boolean equals(int x,int y,int z) {
        switch(this.toward)
        {
            case -1:
                return this.x == x && this.y == y && (z == -10 || this.z == z);
            case 0:
                return this.x == x && (this.y+1) == y && (z == -10 || this.z == z);
            case 1:
                return this.x == x && (this.y-1) == y && (z == -10 || this.z == z);
            case 2:
                return (this.x-1) == x && this.y == y && (z == -10 || this.z == z);
            case 3:
                return (this.x+1) == x && this.y == y && (z == -10 || this.z == z);
        }
        return false;
    }
}
