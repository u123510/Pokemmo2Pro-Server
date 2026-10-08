package org.pokemmo.gameserver.protocol.packets.s2c;
import org.pokemmo.gameserver.codecs.*;
import org.pokemmo.gameserver.game.battle.*;
import org.pokemmo.gameserver.game.battle.effect.CooperativeTeam;
import org.server.OutgoingPacket;
import org.server.Session;
import org.server.bytes.ByteBufEx;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class SendBattleInitPacket extends OutgoingPacket {
    private final BattleManager battleManager;
    private final Session playerSession;
    private final boolean isReconnect;
    private final boolean isSpectate;
    @Override
    public void encode(ByteBufEx buffer) throws Exception {
        byte playerFactionIndex = isSpectate ? (byte) -1 : battleManager.getFactionIndexBySession(playerSession);
        byte playerTeamInDebutIndex = isSpectate ? (byte) -1 : 0;
        PlayerTeam playerTeam = null;
        if (!isSpectate) {
            if (playerFactionIndex < 0 || playerFactionIndex >= battleManager.debutFactions.size()) {
                throw new IllegalStateException("战斗初始化找不到玩家阵营");
            }
            FactionData playerFactionData = battleManager.debutFactions.get(playerFactionIndex);
            playerTeamInDebutIndex = playerFactionData.getPlayerSessionDebutIndex(playerSession);
            BattleTeam battleTeam = playerFactionData.getFactionTeamByTeamIndex(playerTeamInDebutIndex);
            if (!(battleTeam instanceof PlayerTeam)) {
                throw new IllegalStateException("战斗初始化找不到玩家队伍");
            }
            playerTeam = (PlayerTeam) battleTeam;
        } else {
            for (FactionData factionData : battleManager.debutFactions) {
                BattleTeam battleTeam = factionData.getFactionTeamByTeamIndex(0);
                if (battleTeam instanceof PlayerTeam candidate) {
                    playerTeam = candidate;
                    break;
                }
            }
        }
        BattleStateType battleStateType = playerTeam == null
                ? BattleStateType.STATE_STARTED
                : playerTeam.getBattleStateType();
        BattleBassisInfoCodec battleBassisInfoCodec = new BattleBassisInfoCodec((byte) battleManager.debutFactions.size(), playerFactionIndex, playerTeamInDebutIndex, isReconnect, isSpectate, battleStateType);
        //先对战斗的基本信息进行发送
        battleBassisInfoCodec.encode(buffer, battleManager.battleBasisInfo);
        //对每个阵营的队伍进行发送
        for (FactionData factionData : battleManager.debutFactions) {
            //先对队伍阵营进行编码
            BattleTeam factionTeam = factionData.getFactionTeam().getBattleTeam();
            if (factionTeam.getTeamType() == BattleTeamType.COOPERATIVE_PLAYERS || factionTeam.getTeamType() == BattleTeamType.COOPERATIVE_NPC) {
                for (DebutBattleTeam debutBattleTeam : ((CooperativeTeam) factionTeam).getCoordinateActiveTeamMap().values()) {
                    //编码阵营多支队伍信息
                    BattleTeamInfoCodec teamInfoCodec = new BattleTeamInfoCodec(battleManager.battleBasisInfo.isHasInterruption());
                    teamInfoCodec.encode(buffer, debutBattleTeam.getBattleTeam());
                }
            } else {
                //编码阵营单支队伍信息
                BattleTeamInfoCodec teamInfoCodec = new BattleTeamInfoCodec(battleManager.battleBasisInfo.isHasInterruption());
                teamInfoCodec.encode(buffer, factionTeam);
            }
            //获取队伍可登场宝可梦的大小
            int teamCanDebutPokemonSize = factionTeam.getTeamCanDebutPokemonSize();
            if (factionTeam.getTeamType() != BattleTeamType.NULL) {
                //如果玩家的队伍状态机是预览状态，发送预览宝可梦信息
                if (battleStateType == BattleStateType.STATE_TEAM_PREVIEW) {
                    buffer.writeByte(teamCanDebutPokemonSize);
                    for (BattlePokemonData preViewPokemonData : factionTeam.getTeamPokemons()) {
                        BattlePreviewPokemonCodec battlePreviewPokemonCodec = new BattlePreviewPokemonCodec();
                        battlePreviewPokemonCodec.encode(buffer, preViewPokemonData);
                    }
                }
                //如果玩家的队伍状态机不是预览状态 或者玩家的阵营是当前的阵营，则发送队伍宝可梦数据与登场宝可梦数据
                else if(isSpectate || battleStateType != BattleStateType.STATE_TEAM_PREVIEW_SELF_READONLY
                        || playerFactionIndex == factionData.getFactionIndex()) {
                    //获取当前阵营是否是玩家阵营
                    boolean isSelfFaction = playerFactionIndex == factionData.getFactionIndex();
                    //获取阵营的场地信息
                    BattleFactionFieldInfo factionFieldInfo = factionData.getFieldInfo();
                    //编码阵营场地的信息
                    BattleFactionFieldCodec battleFactionFieldCodec = new BattleFactionFieldCodec();
                    battleFactionFieldCodec.encode(buffer,factionFieldInfo);
                    //获取阵营的队伍数量
                    byte factionTeamSize = factionData.getFactionTeamSize();
                    buffer.writeByte(factionTeamSize);
                    //说明是合作对战的队伍类型
                    if(factionTeamSize>1)
                    {
                        for (DebutBattleTeam debutBattleTeam : ((CooperativeTeam)factionTeam).getCoordinateActiveTeamMap().values()){
                            //获取复合类型队伍子队伍的可登场宝可梦的大小
                            int cooperativeTeamCanDebutPokemonSize = debutBattleTeam.getBattleTeam().getTeamCanDebutPokemonSize();
                            buffer.writeByte(cooperativeTeamCanDebutPokemonSize);
                            //对合作队伍中的每一个子队伍可以登场的宝可梦进行编码
                            for (BattlePokemonData pokemonData : debutBattleTeam.getBattleTeam().getTeamCanDebutPokemons()) {
                                BattleTeamPokemonCodec battleTeamPokemonCodec = new BattleTeamPokemonCodec(
                                        battleManager.battleBasisInfo.isReloadBattleStatsBroadcastMode(),
                                        battleManager.battleBasisInfo.getBattleStatsBroadcastMode(),
                                        debutBattleTeam.getTeamCurrentIndex(),
                                        isSelfFaction,
                                        battleManager.battleBasisInfo.getIsShowPokemonAbilityValue()
                                );
                                battleTeamPokemonCodec.encode(buffer,pokemonData);
                            }
                        }
                    }
                    else if(factionTeamSize == 1)
                    {
                        int factionTeamCanDebutPokemonSize = factionTeam.getTeamCanDebutPokemonSize();
                        buffer.writeByte(factionTeamCanDebutPokemonSize);
                        for (BattlePokemonData pokemonData : factionTeam.getTeamCanDebutPokemons()) {
                            BattleTeamPokemonCodec battleTeamPokemonCodec = new BattleTeamPokemonCodec(
                                    battleManager.battleBasisInfo.isReloadBattleStatsBroadcastMode(),
                                    battleManager.battleBasisInfo.getBattleStatsBroadcastMode(),
                                    (byte)0,
                                    isSelfFaction,
                                    battleManager.battleBasisInfo.getIsShowPokemonAbilityValue()
                            );
                            battleTeamPokemonCodec.encode(buffer,pokemonData);
                        }
                    }
                    for(BattlePokemonData battlePokemonData : factionData.getDebutPokemons()) {
                        BattleDebutPokemonCodec battleDebutPokemonCodec = new BattleDebutPokemonCodec(battlePokemonData.getPokemonTeamIndex(),battleManager.battleBasisInfo.getBattleFormType());
                        battleDebutPokemonCodec.encode(buffer, battlePokemonData);
                    }
                }
            }
        }
        if(battleManager.battleBasisInfo.getBattleFormType() == BattleFormType.CONTEST) {
            buffer.writeByte(1);//pokemonContestsCategoryType
            //unuse
            buffer.writeByte(0);
            //pokemonContestsType
            buffer.writeByte(2);
        }
        //编码公共场地信息
        BattlePublicFieldCodec battlePublicFieldCodec = new BattlePublicFieldCodec();
        battlePublicFieldCodec.encode(buffer,battleManager.battleBasisInfo.getBattlePublicFieldInfo());
    }
}
