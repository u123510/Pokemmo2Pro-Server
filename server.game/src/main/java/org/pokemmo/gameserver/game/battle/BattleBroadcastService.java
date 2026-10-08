package org.pokemmo.gameserver.game.battle;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.game.battle.effect.*;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.move.MoveDamageType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.move.PokemonMoveData;
import org.pokemmo.gameserver.game.pokemon.*;
import org.pokemmo.gameserver.game.string.BattleString;
import org.pokemmo.gameserver.game.string.BattleStringType;
import org.pokemmo.gameserver.protocol.GameProtocol;
import org.pokemmo.gameserver.protocol.packets.s2c.*;
import org.server.Session;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

final class BattleBroadcastService extends BattleContextComponent {
    BattleBroadcastService(BattleContextState context) {
        super(context);
    }

    public void broadcastBattlePokemonActionWithNoSelector(BattlePokemonData actionPokemon){
        List<Session> playerSessions = getBattlePlayerSessions();
        for(Session session : playerSessions){
            for(BattlePokemonCauseAction actionPokemonCauseAction: actionPokemon.getCauseWithNoTargetActions()){
                session.send(new SendBattlePokemonActionWithNoSelectorPacket(actionPokemonCauseAction));
            }
        }
    }

    public void broadcastBattlePokemonActionWithSelector() {
        List<Session> playerSessions = getBattlePlayerSessions();
        for(BattlePokemonData actionPokemon : actionPokemons){
            for(Session session : playerSessions){
                if(!actionPokemon.getWithSelectorTargetPokemons().isEmpty() && actionPokemon.getPokemonData().getCurrentHp()>0){
                    long attackerPokemonId = actionPokemon.getPokemonData().getPokemonId();
                    short usedMoveIndexId = actionPokemon.getRoundChoiceMoveIndexId();
                    byte contestIndexData = actionPokemon.getSelectorData();
                    session.send(new SendBattlePokemonActionWithSelectorPacket(attackerPokemonId, usedMoveIndexId, contestIndexData,actionPokemon.getWithSelectorTargetPokemons()));
                }
            }
        }
    }

    public void broadcastBattlePokemonSwap(BattlePokemonData actionPokemon) {
        if (actionPokemon == null || actionPokemon.getTargetPokemon() == null) {
            return;
        }
        BattlePokemonData swapPokemon = actionPokemon.getTargetPokemon();
        //获取更换的目标宝可梦的登场阵营
        byte swapPokemonDebutFactionIndex = swapPokemon.getDebutFactionIndex();
        if (swapPokemonDebutFactionIndex < 0 || swapPokemonDebutFactionIndex >= debutFactions.size()) {
            return;
        }
        byte swapPokemonTeamIndex = debutFactions.get(swapPokemonDebutFactionIndex).getPokemonTeamIndex(swapPokemon);
        if (swapPokemonTeamIndex < 0) {
            return;
        }
        for (Session session : getBattlePlayerSessions()) {
            boolean isSelfFaction = getFactionIndexBySession(session) == swapPokemonDebutFactionIndex;
            session.send(new SendSwapPokemonPacket(
                    actionPokemon.getSelectorData(), isSelfFaction, swapPokemon,
                    new BattleTeamPokemonCodec(this.battleBasisInfo.isReloadBattleStatsBroadcastMode(),
                            this.battleBasisInfo.getBattleStatsBroadcastMode(), swapPokemonTeamIndex,
                            isSelfFaction, this.battleBasisInfo.getIsShowPokemonAbilityValue()),
                    new BattleDebutPokemonCodec(swapPokemonTeamIndex, this.battleBasisInfo.getBattleFormType())));
        }
    }

    public void broadcastBattleRoundUpdate() {
        List<Session> playerSessions = getBattlePlayerSessions();
        for(Session session : playerSessions){
            session.send(new SendBattleRoundSelectorPacket(battleBasisInfo.getBattleRoundAmount()));
        }
    }
}

