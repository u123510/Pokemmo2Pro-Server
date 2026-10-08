package org.pokemmo.gameserver.game.battle;

import org.server.bytes.ByteBufEx;

public class BattleBasisUiInfo {
    private BattleFormatType battleType;
    private BattleFormType battleLevelType;
    private BattleFacilityType battleFacilityType;
    private int beforeDelay;
    private short fightRoundAmount = 1;
    private BattleStateType battleStateType;
    private boolean isIngoreAnimation = false;
    private boolean isCanCloseFight = false;
    public void encode(ByteBufEx buffer){
        buffer.writeByte(battleType.getType());//battleType
        buffer.writeByte(battleLevelType.getType());//battleLevelType
        buffer.writeByte(battleFacilityType.getType());//battleFacilityIndex
        buffer.writeIntLE(beforeDelay);//beforeDelay
        buffer.writeShortLE(fightRoundAmount);//fightRoundAmount
        buffer.writeByte(battleStateType.getType());//BattleStateValue
        buffer.writeBoolean(isIngoreAnimation);//isIngoreAnimation
        buffer.writeBoolean(isCanCloseFight);//isCanCloseFight
    }
    public BattleBasisUiInfo(BattleFormatType battleType, BattleFormType battleLevelType, BattleFacilityType battleFacilityType, int beforeDelay, int fightRoundAmount, BattleStateType battleStateType, boolean isIngoreAnimation, boolean isCanCloseFight) {
        this.battleType = battleType;
        this.battleLevelType = battleLevelType;
        this.battleFacilityType = battleFacilityType;
        this.beforeDelay = beforeDelay;
        this.fightRoundAmount = (short) fightRoundAmount;
        this.battleStateType = battleStateType;
        this.isIngoreAnimation = isIngoreAnimation;
        this.isCanCloseFight = isCanCloseFight;
    }
}
