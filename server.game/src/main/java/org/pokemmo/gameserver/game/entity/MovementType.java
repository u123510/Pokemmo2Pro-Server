package org.pokemmo.gameserver.game.entity;

public enum MovementType {
    NONE(0),
    LOOK_AROUND(1),
    WALK_AROUND(2),
    WALK_UP_AND_DOWN(3),
    WALK_LEFT_AND_RIGHT(4),
    LOOK_SOUTH(5),
    LOOK_UP_AND_DOWN(6),
    LOOK_LEFT_AND_RIGHT(7),
    LOOK_UP_AND_LEFT(8),
    LOOK_UP_AND_RIGHT(9),
    LOOK_DOWN_AND_LEFT(10),
    LOOK_DOWN_AND_RIGHT(11),
    LOOK_UP_DOWN_AND_LEFT(12),
    LOOK_UP_DOWN_AND_RIGHT(13),
    LOOK_UP_LEFT_AND_RIGHT(14),
    LOOK_DOWN_LEFT_AND_RIGHT(15),
    LOOK_AROUND_COUNTER_LOCK_WISE(16),
    LOOK_AROUND_CLOCK_WISE(17),
    CUSTOM_PUMPKING(18),
    CUSTOM_HITODAMA(19),
    CUSTOM_ALPHA_SPAWNER(20);
    private byte movementType;
    private MovementType(int movementType) {
        this.movementType = (byte) movementType;
    }
    public byte getMovementType() {
        return movementType;
    }
}
