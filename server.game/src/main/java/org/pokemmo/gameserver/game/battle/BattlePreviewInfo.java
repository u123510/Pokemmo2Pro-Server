package org.pokemmo.gameserver.game.battle;

import org.server.bytes.ByteBufEx;

public class BattlePreviewInfo {
    private byte enemyConfirmed;
    private boolean isHasPreviewTimeLimit;
    private boolean isTimeLimitEnabled;
    private short presetTotalTime;
    private short oneTurnMaxLimitTime;
    public void encode(ByteBufEx buffer, BattleStateType battleState){
        if(battleState.getIsPreview()){
            buffer.writeByte(enemyConfirmed);
            buffer.writeBoolean(isHasPreviewTimeLimit);
            if(isHasPreviewTimeLimit){
                buffer.writeBoolean(isTimeLimitEnabled);
                buffer.writeShortLE(presetTotalTime);
                buffer.writeShortLE(oneTurnMaxLimitTime);
            }
        }
    }
}
