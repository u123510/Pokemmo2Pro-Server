package org.pokemmo.gameserver.game.battle;

public enum BattleType {
    WildBattle(0,true,true,false,-1),
    PlayerBattle(1,false,false,true,0),
    LowTrainerBattle(2,false,true,true,4),//低等训练家，路边npc
    AceTrainerBattle(3,false,true,true,4),//精英训练家
    RivalBattle(4,false,true,true,4),//劲敌的战斗
    GymLeaderBattle(5,false,true,true,4),//道馆馆主的战斗
    EliteBattle(6,false,true,true,4),//天王战斗
    CooperativeTrainerBattle(7,false,false,true,0),//玩家与npc的合作战斗
    BossBattle(8,false,true,false,4),//boss副本战斗
    CooperativeBossBattle(9,false,false,false,0);//合作boss副本战斗
    private byte type;
    private boolean canRun;
    private boolean canUseItem;
    private boolean isPve;
    private byte canUseItemAmount;

    BattleType(int type,boolean isCanRun,boolean canUseItem,boolean isPve,int canUseItemAmount) {
        this.type = (byte) type;
        this.canRun = isCanRun;
        this.canUseItem = canUseItem;
        this.isPve = isPve;
        this.canUseItemAmount = (byte) canUseItemAmount;
    }
     public byte getType() {
        return type;
    }
     public boolean getCanRun() {
        return canRun;
    }
     public boolean getCanUseItem() {
        return canUseItem;
    }
     public byte getCanUseItemAmount() {
        return canUseItemAmount;
    }
     public boolean isPve() {
        return isPve;
    }
}
