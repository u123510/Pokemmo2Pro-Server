package org.pokemmo.gameserver.game.platform;

public enum CpuArchitectureType {
    x86(0),
    ARM(1),
    RISCV(2),
    LOONGARCH(3);
    private static final CpuArchitectureType[] allTypeArray = {x86, ARM, RISCV, LOONGARCH};
    private byte type;
    CpuArchitectureType(int type) {
        this.type = (byte) type;
    }
    public byte getType() {
        return type;
    }
    public static CpuArchitectureType getByType(byte type) {
        return allTypeArray[type];
    }
}
