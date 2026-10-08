package org.pokemmo.gameserver.protocol.packets.c2s;

import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.character.CharacterManager;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.server.IncomingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;
import org.pokemmo.gameserver.game.battle.BattleFormatType;
import org.pokemmo.gameserver.game.battle.BattlePokemonCommandType;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleDebutPokemonCanActionPacket;

public class BattlePokemonCommandPacket extends IncomingPacket {
    private byte commandUserData;
    private BattlePokemonCommandType commandType;
    private short moveIndexId;
    private short swapIndex;
    private short itemIndexId;
    private long itemTargetId;
    private byte commandTargetData;
    private boolean hasCommandTargetData;

    private static void requireReadable(ByteBufEx buffer, int length, String field) {
        if (buffer.readableBytes() < length) {
            throw new IllegalArgumentException("战斗行动缺少字段: " + field
                    + ", 需要=" + length + ", 剩余=" + buffer.readableBytes());
        }
    }

    @Override
    public void decode(ByteBufEx buffer) {
        requireReadable(buffer, 2, "commandUserData/commandType");
        commandUserData = buffer.readByte();
        int commandTypeValue = buffer.readUnsignedByte();
        if (commandTypeValue > BattlePokemonCommandType.UNK2.getType()) {
            throw new IllegalArgumentException("未知战斗命令类型: " + commandTypeValue);
        }
        commandType = BattlePokemonCommandType.getByType((byte) commandTypeValue);
        switch (commandType) {
            case MOVE:
                requireReadable(buffer, 2, "moveIndexId");
                moveIndexId = buffer.readShortLE();
                // 单打客户端不会发送目标字节，服务端随后按战斗形式推导目标。
                if (buffer.isReadable()) {
                    requireReadable(buffer, 1, "commandTargetData");
                    commandTargetData = buffer.readByte();
                    hasCommandTargetData = true;
                }
                return;
            case SWAP:
            case COWER:
                requireReadable(buffer, 2, "swapIndex");
                swapIndex = buffer.readShortLE();
                return;
            case ITEM:
                requireReadable(buffer, 10, "itemIndexId/itemTargetId");
                itemIndexId = buffer.readShortLE();
                itemTargetId = buffer.readLongLE();
                break;
            default:
                return;
        }
        requireReadable(buffer, 1, "commandTargetData");
        commandTargetData = buffer.readByte();
        hasCommandTargetData = true;
    }

    @Override
    public void handle(Session session) throws Exception {
        //解析命令使用方阵营，与目标宝可梦索引
        CharacterManager characterManager = session.attr(GameProtocol.ATTRIBUTE_CHARACTER_MANAGER).get();
        BattleManager battleManager = characterManager.getBattleManager();
        if (battleManager != null && battleManager.isSpectator(session)) {
            return;
        }
        byte selectorFaction = (byte) (commandUserData&0x0F);
        byte selectorPokemonInDebutTeamIndex = (byte) (commandUserData>>4);
        if (battleManager == null || selectorFaction < 0
                || selectorFaction >= battleManager.debutFactions.size()
                || selectorPokemonInDebutTeamIndex < 0
                || selectorPokemonInDebutTeamIndex >= battleManager.debutFactions
                .get(selectorFaction).getDebutPokemons().length) {
            return;
        }
        if (commandType == BattlePokemonCommandType.MOVE
                && !hasCommandTargetData
                && battleManager.battleBasisInfo.getBattleFormatType()
                != BattleFormatType.SINGLE_BATTLE) {
            return;
        }
        //获取命令执行者的回合
        Session commandSession = battleManager.getPlayerSessionByFactionIndexAndTeamIndex(selectorFaction,selectorPokemonInDebutTeamIndex);
        //只用当命令执行者的会话与当前会话一致时，才执行命令，否则可能说明命令被伪造
        boolean commandAccepted = false;
        if(commandSession == session && selectorFaction >= 0 && selectorFaction < battleManager.debutFactions.size()){
            byte targetFaction = (byte) (commandTargetData&0x0F);
            byte targetPokemonInDebutTeamIndex = (byte) ((commandTargetData >>> 4) & 0x0F);
            switch (commandType)
            {
                case MOVE:
                    commandAccepted = battleManager.handlePlayerUseMove(selectorFaction, selectorPokemonInDebutTeamIndex, targetFaction, targetPokemonInDebutTeamIndex,moveIndexId);
                    break;
                case SWAP:
                    commandAccepted = battleManager.handlePlayerSwap(selectorFaction,selectorPokemonInDebutTeamIndex,swapIndex);
                    break;
                case ITEM:
                    commandAccepted = battleManager.handlePlayerUseItem(itemIndexId,itemTargetId,selectorFaction, selectorPokemonInDebutTeamIndex);
                    break;
                case RUN:
                    commandAccepted = battleManager.runAway(session, commandUserData);
                    break;
                case FORFEIT:
                    commandAccepted = battleManager.forfeit(session, commandUserData);
                    break;
                case COWER:
                    break;
            }
        }
        // 当命令未被接受且战斗仍在进行时，通知客户端重新开放该槽位行动，避免客户端锁死
        if (!commandAccepted && battleManager.isFactionInBattle(selectorFaction)) {
            session.send(new SendBattleDebutPokemonCanActionPacket(selectorPokemonInDebutTeamIndex, true));
        }
        /*else{
            List<BattleString> battleStringList_0 = new ArrayList<>(0);
            battleStringList_0.add(new BattleString(BattleStringType.NULL_TYPE));
            List<BattleString> battleStringList_1 = new ArrayList<>(0);
            battleStringList_0.add(new BattleString(BattleStringType.NULL_TYPE));
            List<BattleString> battleStringList_2 = new ArrayList<>(0);
            //通知客户端战斗结束
            //结算玩家金钱
            //关闭对战
            if(battleManager.debutFactions.get(selectorFaction).getFactionStatType() == FactionResultType.VICTORY) {
                session.send(new SendBattleFinishPacket(selectorFaction,battleStringList_0,battleStringList_1,0,0,false,battleStringList_2));
            }
            if(battleManager.debutFactions.get(selectorFaction).getFactionStatType() == FactionResultType.DEFEAT) {
                session.send(new SendBattleFinishPacket(targetFaction,battleStringList_0,battleStringList_1,0,0,false,battleStringList_2));
            }
        }*/
    }
}
