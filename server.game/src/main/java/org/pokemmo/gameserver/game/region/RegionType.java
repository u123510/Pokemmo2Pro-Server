package org.pokemmo.gameserver.game.region;

public enum RegionType {
    KANTO(0, "kanto"),
    HOENN(1, "hoenn"),
    UNOVA(2, "unova"),
    SINNOH(3, "sinnoh"),
    CUSTOM(10, "custom"),
    ACTIVE(128, "active");
    private byte regionIndexId;
    private String name;
    RegionType(int regionIndexId, String name) {
        this.regionIndexId = (byte) regionIndexId;
        this.name = name;
    }
    public static boolean isGBA(int regionId) {
        return regionId == 0 || regionId == 1;
    }

    public static boolean isNDS(int regionId) {
        return regionId == 2 || regionId == 3;
    }
    public byte getType() {
        return regionIndexId;
    }
    public String getName() {
        return name;
    }
    public static RegionType getByType(int type) {
        for (RegionType region : RegionType.values()) {
            if (region.getType() == type) {
                return region;
            }
        }
        return null;
    }
    public static RegionType getByName(String name) {
        for (RegionType region : RegionType.values()) {
            if (region.getName().equals(name)) {
                return region;
            }
        }
        return null;
    }
}
