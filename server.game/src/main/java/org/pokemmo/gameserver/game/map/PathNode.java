package org.pokemmo.gameserver.game.map;

public class PathNode implements Comparable<PathNode> {
    private short x;
    private short y;
    private int g;
    private int h;
    private int f; // f = g + h
    private PathNode parent;

    public PathNode(short x, short y, PathNode parent, int g, int h) {
        this.x = x;
        this.y = y;
        this.parent = parent;
        this.g = g;
        this.h = h;
        this.f = g + h;
    }
    public short getX() {
        return x;
    }
    public short getY() {
        return y;
    }
    public int getG() {
        return g;
    }
    public int getH() {
        return h;
    }
    public int getF() {
        return f;
    }
    public PathNode getParent() {
        return parent;
    }
    @Override
    public int compareTo(PathNode other) {
        return Integer.compare(this.f, other.f);
    }
    public static int getManhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}
