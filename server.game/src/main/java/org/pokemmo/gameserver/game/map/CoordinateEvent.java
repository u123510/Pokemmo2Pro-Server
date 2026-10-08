package org.pokemmo.gameserver.game.map;

import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class CoordinateEvent {
    private short x;
    private short y;
    private byte z;
    private String eventName;

    public CoordinateEvent(String eventName, int x, int y, int z) {
        this.eventName = eventName;
        this.x = (short) x;
        this.y = (short) y;
        this.z = (byte) z;
    }
    public boolean equals(int x,int y,int z) {
        return this.x == x && this.y == y && this.z == z;
    }
}
