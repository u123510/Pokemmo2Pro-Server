package org.pokemmo.gameserver.game.map;

import lombok.Getter;

@Getter
public class MapCoordinateData {
    private short tileX;
    private short tileY;
    private short tileId;
    private byte tileAttribute;
    private KantoMetaTileBehaviorType metatileBehaviorType;
    public MapCoordinateData(short tileX, short tileY, short tileId, byte tileAttribute, byte metatileBehaviorType) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.tileId = tileId;
        this.tileAttribute = tileAttribute;
        this.metatileBehaviorType = KantoMetaTileBehaviorType.getByType(metatileBehaviorType);
    }

    public short getTileX() {
        return tileX;
    }

    public void setTileX(short tileX) {
        this.tileX = tileX;
    }

    public short getTileY() {
        return tileY;
    }

    public void setTileY(short tileY) {
        this.tileY = tileY;
    }

    public short getTileId() {
        return tileId;
    }

    public void setTileId(short tileId) {
        this.tileId = tileId;
    }

    public byte getTileAttribute() {
        return tileAttribute;
    }

    public void setTileAttribute(byte tileAttribute) {
        this.tileAttribute = tileAttribute;
    }

    public byte getZCoordinate() {
        byte z = (byte) ((this.tileAttribute >> 2) - 1);
        if (z == -8) {
            return (byte) 8;
        }
        return z;
    }

    public boolean isObstacle() {
        return (tileAttribute & 0x01) != 0;
    }
    public boolean isWalkable() {
        return !isObstacle();
    }
}
