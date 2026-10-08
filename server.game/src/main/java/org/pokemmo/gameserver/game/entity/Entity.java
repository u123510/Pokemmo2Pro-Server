package org.pokemmo.gameserver.game.entity;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class Entity {
    private long entityGameId;
    private byte regionIndexId;
    private byte mapHeaderIdOrGbaMapGroupId;
    private byte gbaMapId;
    private short x;
    private short y;
    private byte z;
    private byte toward;
    public abstract String getEntityName();
    public Entity(long entityGameId, int regionIndexId, int mapHeaderIdOrGbaMapGroupId, int gbaMapId, int x, int y, int z, int toward) {
        this.entityGameId = entityGameId;
        this.regionIndexId = (byte) regionIndexId;
        this.mapHeaderIdOrGbaMapGroupId = (byte) mapHeaderIdOrGbaMapGroupId;
        this.gbaMapId = (byte) gbaMapId;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
        this.toward = (byte) toward;
    }
    public Entity upDateRegionIndexId(int regionIndexId) {
        this.regionIndexId = (byte) regionIndexId;
        return this;
    }
    public Entity upDateMapHeaderIdOrGbaMapGroupId(int mapHeaderIdOrGbaMapGroupId) {
        this.mapHeaderIdOrGbaMapGroupId = (byte) mapHeaderIdOrGbaMapGroupId;
        return this;
    }
    public Entity upDateGbaMapId(int gbaMapId) {
        this.gbaMapId = (byte) gbaMapId;
        return this;
    }
    public Entity upDateX(int x) {
        this.x = (short) x;
        return this;
    }
    public Entity upDateY(int y) {
        this.y = (short) y;
        return this;
    }
    public Entity upDateZ(int z) {
        this.z = (byte) z;
        return this;
    }
    public Entity upDateToward(int toward) {
        this.toward = (byte) toward;
        return this;
    }
}
