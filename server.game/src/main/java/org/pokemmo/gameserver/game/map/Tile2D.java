package org.pokemmo.gameserver.game.map;

public class Tile2D {
    private final short material;
    /**
     *
     */
    private final byte collision;

    public Tile2D(int material,int collision) {
        this.material = (short) material;
        this.collision = (byte) collision;
    }
    public short getMaterial() {
        return material;
    }

    public byte getCollision() {
        return collision;
    }
    public short encode() {
        return (short) ((material & 0x3FF) | (collision << 10));
    }

    public static Tile2D decode(short encoded) {
        return new Tile2D((short) (encoded & 0x3FF), (byte) (encoded >> 10));
    }
}
