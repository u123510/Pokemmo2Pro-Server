package org.pokemmo.gameserver.game.move;
//技能附加的改变宝可梦强化等级的目标类型 -1:技能目标 1:技能使用者
public enum MoveAdditionalStatChangeTargetType {
    TARGET(-1),
    USER(1);
    private byte type;
    private static final MoveAdditionalStatChangeTargetType[] allTypeArray = values();
    MoveAdditionalStatChangeTargetType(int type) {
        this.type = (byte) type;
    }

    public byte getType() {
        return type;
    }
    public static MoveAdditionalStatChangeTargetType getByType(int type) {
        for (MoveAdditionalStatChangeTargetType targetType : allTypeArray) {
            if (targetType.getType() == type) {
                return targetType;
            }
        }
        return null;
    }
}
