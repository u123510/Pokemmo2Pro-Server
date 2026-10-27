package org.pokemmo.gameserver.game.character;

/** Server checkpoint definition. The final two source options remain opaque. */
public record DeathSavePoint(
        int id,
        String key,
        String sourceMap,
        String destinationMap,
        short x,
        short y,
        byte z,
        int option1,
        int option2) {
    public DeathSavePoint {
        if (id < 200 || id > 213 || key == null || !key.matches("UNOVA_[A-Z0-9_]+")
                || sourceMap == null || !sourceMap.matches("Unova_Map_[0-9]{3}")
                || destinationMap == null || !destinationMap.matches("Unova_Map_[0-9]{3}")
                || x < 0 || y < 0 || z < 0) {
            throw new IllegalArgumentException("非法的合众死亡存盘点: " + id);
        }
    }
}
