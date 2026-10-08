package org.pokemmo.gameserver.game.battle;
import lombok.Getter;

import java.util.HashMap;
@Getter
public class BattleFactionFieldInfo {
    private boolean hasFieldInit;
    private HashMap<Byte, Byte> trapInfo;
    private boolean hasPledgeFieldOfFireField;
    private byte pledgeFieldOfFireReaminRound;
    private boolean hasPledgeRainbowField;
    private byte pledgeRainbowRemainRound;
    private boolean hasPledgeSwampField;
    private byte pledgeSwampRemainRound;
    private boolean hasPledgeCureField;
    private byte pledgeCureRemainRound;
    private boolean isInMaelstrom;
    //是否存在顺风场地
    private boolean hasTailWindField;
    private byte tailWindRemainRound;
    private boolean hasEnemyCommand;
    private byte enemyCommandTotalRound;
    private short enemyCommandUseRound;
    private short enemyCommandSkillIndex;
    private byte enemyCommandRemainTurns;
    private boolean hasSelfCommand;
    private byte selfCommandTotalRound;
    private short selfCommandUseRound;
    private short selfCommandSkillIndex;
    private byte selfCommandRemainTurns;
    private boolean hasSafeGuardField;
    private byte safeguardRemainRound;
    private boolean hasMistField;
    private byte mistRemainRound;
    private boolean hasLuckyChantField;
    private byte luckyChantRemainRound;
    private boolean hasStrongWindsField;
    private byte strongWindsFieldRemainRound;
}
