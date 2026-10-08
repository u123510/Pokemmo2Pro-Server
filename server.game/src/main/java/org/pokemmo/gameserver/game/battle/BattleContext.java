package org.pokemmo.gameserver.game.battle;

/** Stable battle context entry point; domain responsibilities are exposed by focused collaborators. */
public class BattleContext extends BattleContextState {
    public BattleContext(BattleType battleType, BattleFormatType battleFormatType,
                         BattleFormType battleFormType, BattleFacilityType battleFacilityType,
                         FactionData selfFaction, FactionData enemyFaction,
                         int battleAlreadyRunTime) {
        super(battleType, battleFormatType, battleFormType, battleFacilityType,
                selfFaction, enemyFaction, battleAlreadyRunTime);
    }
}
