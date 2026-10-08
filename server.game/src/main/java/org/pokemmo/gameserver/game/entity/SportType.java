package org.pokemmo.gameserver.game.entity;

import java.util.List;

//from 0 to 314
public enum SportType {
    LOOK_UP(1,1,0,15),
    LOOK_LEFT(2,2,0,15),
    LOOK_RIGHT(3,3,0,15),
    LOOK_DOWN(4,0,0,15),
    WALK_DOWN_VSLOW(8,0,1,525),
    WALK_UP_VSLOW(9,1,1,525),
    WALK_LEFT_VSLOW(10,2,1,525),
    WALK_RIGHT_VSLOW(11,3,1,525),
    WALK_DOWN_SLOW(12,0,1,390),
    WALK_UP_SLOW(13,1,1,390),
    WALK_LEFT_SLOW(14,2,1,390),
    WALK_RIGHT_SLOW(15,3,1,390),
    WALK_DOWN(16,0,1,275),
    WALK_UP(17,1,1,275),
    WALK_LEFT(18,2,1,275),
    WALK_RIGHT(19,3,1,275),
    JUMP_DOWN2(20,0,2,550),
    JUMP_UP2(21,1,2,550),
    JUMP_LEFT2(22,2,2,550),
    JUMP_RIGHT2(23,3,2,550),
    PAUSE_XSHORT(24,-1,0,17),
    PAUSE_VSHORT(25,-1,0,34),
    PAUSE_SHORT(26,-1,0,68),
    PAUSE(27,-1,0,135),
    PAUSE_LONG(28,-1,0,275),
    RUN_DOWN(29,0,1,135),
    RUN_UP(30,1,1,135),
    RUN_LEFT(31,2,1,135),
    RUN_RIGHT(32,3,1,135),
    ONSPOT_DOWN_SLOW(33,0,0,80),
    ONSPOT_UP_SLOW(34,1,0,80),
    ONSPOT_LEFT_SLOW(35,2,0,80),
    ONSPOT_RIGHT_SLOW(36,3,0,80),
    ONSPOT_DOWN(37,0,0,275),
    ONSPOT_UP(38,1,0,275),
    ONSPOT_LEFT(39,2,0,275),
    ONSPOT_RIGHT(40,3,0,275),
    ONSPOT_DOWN_FAST(41,0,0,30),
    ONSPOT_UP_FAST(42,1,0,30),
    ONSPOT_LEFT_FAST(43,2,0,30),
    ONSPOT_RIGHT_FAST(44,3,0,30),
    LOOK_DOWN_DELAYED(45,0,0,68),
    LOOK_UP_DELAYED(46,1,0,68),
    LOOK_LEFT_DELAYED(47,2,0,68),
    LOOK_RIGHT_DELAYED(48,3,0,68),
    SLIDE_DOWN(49,0,1,200),
    SLIDE_UP(50,1,1,200),
    SLIDE_LEFT(51,2,1,200),
    SLIDE_RIGHT(52,3,1,200),
    RUN_DOWN_VFAST(53,0,1,80),
    RUN_UP_VFAST(54,1,1,80),
    RUN_LEFT_VFAST(55,2,1,80),
    RUN_RIGHT_VFAST(56,3,1,80),
    SLIDE_DOWN_VFAST(57,0,1,25),
    SLIDE_UP_VFAST(58,1,1,25),
    SLIDE_LEFT_VFAST(59,2,1,25),
    SLIDE_RIGHT_VFAST(60,3,1,25),
    SLIDE_DOWN2(61,0,1,30),
    SLIDE_UP2(62,1,1,30),
    SLIDE_LEFT2(63,2,1,30),
    SLIDE_RIGHT2(64,3,1,30),
    SLIDE_DOWN3(65,0,1,30),
    SLIDE_UP3(66,1,1,30),
    SLIDE_LEFT3(67,2,1,30),
    SLIDE_RIGHT3(68,3,1,30),
    WALK_ONSPOT(69,-1,0,30),
    LOOK_LEFT_JUMP_DOWN(70,2|0,2,30),
    LOOK_DOWN_JUMP_UP(71,0|1,2,30),
    LOOK_UP_JUMP_LEFT(72,0|2,2,30),
    LOOK_LEFT_JUMP_RIGHT(73 ,2|3,2,30),
    FACE_PLAYER(74,-2,0,30),
    FACE_AWAY(75,-3,0,30),
    JUMP_DOWN1(78,0,2,30),
    JUMP_UP1(79,1,2,30),
    JUMP_LEFT1(80,2,2,30),
    JUMP_RIGHT1(81,3,2,30),
    JUMP_DOWN(82,0,2,275),
    JUMP_UP(83,1,2,275),
    JUMP_LEFT(84,2,2,275),
    JUMP_RIGHT(85,2,2,275),
    JUMP_DOWNUP(86,0|1,2,275),
    JUMP_UPDOWN(87,1|0,2,275),
    JUMP_LEFTRIGHT(88,2|3,2,275),
    JUMP_RIGHTLEFT(89,3|2,2,275),
    FACE_DEFAULT(90,-1,0,15),
    HIDE(96,-1,0,100),
    SHOW(97,-1,0,100),
    SAY_SINGLE_EXCLAMATION(98,-1,0,750),
    SAY_SINGLE_QUESTION_MARK(99,-1,0,750),
    SAY_X(100,-1,0,750),
    SAY_DOUBLE_EXCLAMATION(101,-1,0,1500),
    SAY_SMILIE(102,-1,0,750),
    ROCK_ANIMATE(104,-1,0,500),
    TREE_ANIMATE(105,-1,0,500),
    SPIN_DOWN(112,0,1,200),
    SPIN_UP(113,1,1,200),
    SPIN_LEFT(114,2,1,200),
    SPIN_RIGHT(115,3,1,200),
    USE_ROD(116,-1,0,3000),
    USE_ROD_CATCH(117,-1,0,750),
    TRAPDOOR_FALL(118,-1,0,500),
    USE_HM(119,-1,0,2000),
    HEADING_CHANGES_DISABLED(-128,-1,0,128),
    HEADING_CHANGES_ENABLED(-127,-1,0,129),
    WALK_DIAG_UPRIGHT(-111,-1,0,275),
    WALK_DIAG_DOWNLEFT(-110,-1,0,275),
    WALK_DIAG_UPLEFT(-109,-1,0,275),
    WALK_DIAG_DOWNRIGHT(-108,-1,0,275),
    RESET_CAMERA(-16,-1,0,30),
    JUMP_ONSPOT(-15,-1,0,550),
    FALL_INTO_HOLE(-14,-1,0,500);
    private byte type;
    private byte moveToward;
    private byte moveDistance;
    private int actionTimeConsuming;
    SportType(int type,int moveToward,int moveDistance,int actionTimeConsuming) {
        this.type = (byte) type;
        this.moveToward = (byte) moveToward;
        this.moveDistance = (byte) moveDistance;
        this.actionTimeConsuming = actionTimeConsuming;
    }
    public byte getType() {
        return type;
    }
    public byte getMoveToward() {
        return moveToward;
    }
    public byte getMoveDistance() {
        return moveDistance;
    }
    public static SportType getSportType(int type) {
        for (SportType sportType : values()) {
            if (sportType.getType() == type) {
                return sportType;
            }
        }
        return null;
    }
    public static int getActionTimeConsuming(SportType[] sportTypes) {
        int actionTimeConsuming = 0;
        for (SportType sportType : sportTypes) {
            actionTimeConsuming += sportType.actionTimeConsuming;
        }
        return actionTimeConsuming;
    }
    public static int getActionTimeConsuming(List<SportType> sportTypes) {
        int actionTimeConsuming = 0;
        for (SportType sportType : sportTypes) {
            actionTimeConsuming += sportType.actionTimeConsuming;
        }
        return actionTimeConsuming;
    }
}
