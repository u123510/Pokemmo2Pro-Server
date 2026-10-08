package org.pokemmo.gameserver.game.battle;

import lombok.extern.slf4j.Slf4j;
import org.pokemmo.gameserver.codecs.BattleDebutPokemonCodec;
import org.pokemmo.gameserver.codecs.BattleTeamPokemonCodec;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleDebutPokemonCanActionPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendBattleWaitForPlayerActionPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendPokemonDiedPacket;
import org.pokemmo.gameserver.protocol.packets.s2c.SendSwapPokemonPacket;
import org.server.Session;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责战斗中的宝可梦换人（主动常规轮换与阵亡濒死替补）领域服务。
 */
@Slf4j
final class BattleSwitchService extends BattleContextComponent {
    private static final long SWITCH_PRESENTATION_DELAY_MILLIS = 2_000L;
    private static final ScheduledExecutorService SWITCH_PRESENTATION_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "battle-switch-presentation");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private final Set<Byte> waitingFaintSlots = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean nextRoundScheduled = new AtomicBoolean();

    BattleSwitchService(BattleContextState context) {
        super(context);
    }

    public boolean isWaitingForFaintReplacement() {
        return !waitingFaintSlots.isEmpty();
    }

    public boolean isSlotWaitingForFaintReplacement(byte factionIndex, byte slotIndex) {
        return waitingFaintSlots.contains(toSelectorData(factionIndex, slotIndex));
    }

    public void clearWaitingFaintSlots() {
        waitingFaintSlots.clear();
    }

    public boolean isPokemonOnField(BattlePokemonData pokemon) {
        if (pokemon == null || pokemon.getPokemonData() == null) {
            return false;
        }
        long pokemonId = pokemon.getPokemonData().getPokemonId();
        for (FactionData faction : debutFactions) {
            for (BattlePokemonData debut : faction.getDebutPokemons()) {
                if (debut != null && debut.getPokemonData() != null
                        && debut.getPokemonData().getPokemonId() == pokemonId) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkAndPromptFaintReplacements() {
        boolean anyPlayerWaiting = false;
        boolean anyNpcReplacement = false;
        for (byte f = 0; f < debutFactions.size(); f++) {
            FactionData factionData = debutFactions.get(f);
            if (!context.checkFactionHasAlive(f)) {
                continue;
            }
            BattlePokemonData[] debutPokemons = factionData.getDebutPokemons();
            for (byte s = 0; s < debutPokemons.length; s++) {
                BattlePokemonData debutPokemon = debutPokemons[s];
                if (debutPokemon != null && debutPokemon.getPokemonData() != null
                        && debutPokemon.getPokemonData().getCurrentHp() <= 0) {
                    byte selectorData = toSelectorData(f, s);
                    Session ownerSession = debutPokemon.getOwnerSession();
                    if (ownerSession != null && ownerSession.isActive()) {
                        waitingFaintSlots.add(selectorData);
                        anyPlayerWaiting = true;
                        ownerSession.send(new SendPokemonDiedPacket(selectorData, false, false));
                    } else {
                        anyNpcReplacement |= autoSwitchNpcPokemon(f, s, debutPokemon);
                    }
                }
            }
        }
        if (anyPlayerWaiting) {
            notifyAlivePlayersWaiting();
            return true;
        }
        if (anyNpcReplacement) {
            scheduleNextRoundAfterSwap();
            return true;
        }
        return false;
    }

    private void notifyAlivePlayersWaiting() {
        for (Session session : getBattlePlayerSessions()) {
            byte playerFaction = getFactionIndexBySession(session);
            boolean isWaiting = false;
            if (playerFaction >= 0 && playerFaction < debutFactions.size()) {
                for (byte s = 0; s < debutFactions.get(playerFaction).getDebutPokemons().length; s++) {
                    if (waitingFaintSlots.contains(toSelectorData(playerFaction, s))) {
                        isWaiting = true;
                        break;
                    }
                }
            }
            if (!isWaiting) {
                session.send(new SendBattleWaitForPlayerActionPacket());
            }
        }
    }

    private boolean autoSwitchNpcPokemon(byte factionIndex, byte slotIndex,
                                         BattlePokemonData faintedPokemon) {
        FactionData factionData = debutFactions.get(factionIndex);
        byte teamIndex = factionData.getPokemonTeamIndex(faintedPokemon);
        if (teamIndex < 0) {
            teamIndex = 0;
        }
        BattleTeam team = factionData.getFactionTeamByTeamIndex(teamIndex);
        if (team == null) {
            return false;
        }
        List<BattlePokemonData> teamPokemons = team.getTeamPokemons();
        for (int i = 0; i < teamPokemons.size(); i++) {
            BattlePokemonData candidate = teamPokemons.get(i);
            if (candidate != null && candidate.getPokemonData() != null
                    && candidate.getPokemonData().getCurrentHp() > 0
                    && !isPokemonOnField(candidate)) {
                doPerformSwap(factionIndex, slotIndex, teamIndex, i, faintedPokemon, candidate);
                log.debug("NPC战斗替补已提交: faction={}, slot={}, oldPokemonId={}, newPokemonId={}, rosterIndex={}, teamIndex={}",
                        factionIndex, slotIndex,
                        faintedPokemon.getPokemonData().getPokemonId(),
                        candidate.getPokemonData().getPokemonId(), i, teamIndex);
                return true;
            }
        }
        return false;
    }

    public boolean handleFaintReplacementSwap(byte swapFaction, byte swapSlot, int swapIndex) {
        if (swapFaction < 0 || swapFaction >= debutFactions.size()
                || swapSlot < 0 || swapSlot >= debutFactions.get(swapFaction).getDebutPokemons().length) {
            return false;
        }
        FactionData factionData = debutFactions.get(swapFaction);
        BattlePokemonData originalPokemonData = factionData.getDebutPokemons()[swapSlot];
        if (originalPokemonData == null || swapIndex < 0) {
            return false;
        }
        byte swapTeamIndex = factionData.getPokemonTeamIndex(originalPokemonData);
        if (swapTeamIndex < 0) {
            swapTeamIndex = 0;
        }
        BattleTeam swapTeam = factionData.getFactionTeamByTeamIndex(swapTeamIndex);
        if (swapTeam == null || swapIndex >= swapTeam.getTeamPokemons().size()) {
            return false;
        }
        BattlePokemonData swapPokemon = swapTeam.getTeamPokemons().get(swapIndex);
        if (swapPokemon == null || swapPokemon.getPokemonData() == null
                || swapPokemon.getPokemonData().getCurrentHp() <= 0
                || isPokemonOnField(swapPokemon)) {
            return false;
        }

        doPerformSwap(swapFaction, swapSlot, swapTeamIndex, swapIndex, originalPokemonData, swapPokemon);

        byte selectorData = toSelectorData(swapFaction, swapSlot);
        waitingFaintSlots.remove(selectorData);

        if (waitingFaintSlots.isEmpty()) {
            scheduleNextRoundAfterSwap();
        }
        return true;
    }

    public void executeInRoundSwap(BattlePokemonData actionPokemon) {
        if (actionPokemon == null || actionPokemon.getTargetPokemon() == null) {
            return;
        }
        BattlePokemonData swapPokemon = actionPokemon.getTargetPokemon();
        byte actionFactionIndex = actionPokemon.getDebutFactionIndex();
        if (actionFactionIndex < 0 || actionFactionIndex >= debutFactions.size()) {
            return;
        }
        byte actionSlotIndex = actionPokemon.getDebutIndex();
        byte swapPokemonTeamIndex = debutFactions.get(actionFactionIndex).getPokemonTeamIndex(swapPokemon);
        byte actionPokemonTeamIndex = debutFactions.get(actionFactionIndex).getPokemonTeamIndex(actionPokemon);
        if (swapPokemonTeamIndex < 0 || swapPokemonTeamIndex != actionPokemonTeamIndex
                || swapPokemon.getPokemonData() == null || swapPokemon.getPokemonData().getCurrentHp() <= 0
                || isPokemonOnField(swapPokemon)) {
            return;
        }
        BattleTeam swapTeam = debutFactions.get(actionFactionIndex).getFactionTeamByTeamIndex(swapPokemonTeamIndex);
        int swapIndex = swapTeam != null ? swapTeam.getTeamPokemons().indexOf(swapPokemon) : 0;
        if (swapIndex < 0) {
            swapIndex = 0;
        }
        doPerformSwap(actionFactionIndex, actionSlotIndex, actionPokemonTeamIndex, swapIndex, actionPokemon, swapPokemon);
    }

    public void doPerformSwap(byte factionIndex, byte slotIndex, byte teamIndex,
                              int swapIndex, BattlePokemonData oldPokemon, BattlePokemonData swapPokemon) {
        FactionData factionData = debutFactions.get(factionIndex);
        byte selectorData = toSelectorData(factionIndex, slotIndex);

        if (!checkIsPlayerFaction(factionIndex) && battleBasisInfo.getBattleType() != BattleType.CooperativeBossBattle) {
            byte playerFaction = getEnemyFactionIndex(factionIndex);
            for (BattlePokemonData pokemon : debutFactions.get(playerFaction).getDebutPokemons()) {
                if (pokemon != null && pokemon.getPokemonData().getCurrentHp() > 0) {
                    swapPokemon.getExpPoolPokemons().add(pokemon);
                }
            }
        }

        swapPokemon.setDebutFactionIndex(factionIndex);
        swapPokemon.setDebutIndex(slotIndex);
        swapPokemon.setPokemonTeamIndex(teamIndex);
        swapPokemon.setFirstRoundDebut(true);
        swapPokemon.setAlreadyUseSkill(false);
        swapPokemon.setBattlePokemonCommandType(BattlePokemonCommandType.NULL);
        factionData.getDebutPokemons()[slotIndex] = swapPokemon;

        if (oldPokemon != null) {
            oldPokemon.clearDataAfterExit();
        }

        broadcastSwapPacket(selectorData, factionIndex, swapPokemon, teamIndex);
    }

    public void broadcastSwapPacket(byte selectorData, byte swapFactionIndex,
                                    BattlePokemonData swapPokemon, int teamIndex) {
        /*
         * The first field in both battle codecs is the active battle-team index,
         * not the Pokemon's position inside that team's roster. Single battles
         * therefore keep this value at 0 even when the second roster Pokemon
         * (position 1, 2, ...) is sent into the active slot.
         */
        byte teamPos = (byte) teamIndex;
        for (Session session : getBattlePlayerSessions()) {
            boolean isSelfFaction = getFactionIndexBySession(session) == swapFactionIndex;
            session.send(new SendSwapPokemonPacket(
                    selectorData,
                    isSelfFaction,
                    swapPokemon,
                    new BattleTeamPokemonCodec(battleBasisInfo.isReloadBattleStatsBroadcastMode(),
                            battleBasisInfo.getBattleStatsBroadcastMode(),
                            teamPos,
                            isSelfFaction,
                            battleBasisInfo.getIsShowPokemonAbilityValue()),
                    new BattleDebutPokemonCodec(teamPos, battleBasisInfo.getBattleFormType())));
        }
        BattleManager battleManager = context instanceof BattleManager manager ? manager : null;
        if (battleManager != null) {
            for (Session spectatorSession : battleManager.getSpectatorSessions()) {
                spectatorSession.send(new SendSwapPokemonPacket(
                        selectorData,
                        false,
                        swapPokemon,
                        new BattleTeamPokemonCodec(battleBasisInfo.isReloadBattleStatsBroadcastMode(),
                                battleBasisInfo.getBattleStatsBroadcastMode(),
                                teamPos,
                                false,
                                battleBasisInfo.getIsShowPokemonAbilityValue()),
                        new BattleDebutPokemonCodec(teamPos, battleBasisInfo.getBattleFormType())));
            }
        }
    }

    public void startNextRound() {
        battleBasisInfo.setBattleRoundAmount((short) (battleBasisInfo.getBattleRoundAmount() + 1));
        broadcastBattleRoundUpdate();
        for (FactionData faction : debutFactions) {
            for (BattlePokemonData debutPokemon : faction.getDebutAlivePokemons()) {
                Session ownerSession = debutPokemon.getOwnerSession();
                if (ownerSession != null && ownerSession.isActive()) {
                    ownerSession.send(new SendBattleDebutPokemonCanActionPacket(debutPokemon.getDebutIndex(), true));
                }
            }
        }
    }

    private void scheduleNextRoundAfterSwap() {
        if (!nextRoundScheduled.compareAndSet(false, true)) {
            return;
        }
        SWITCH_PRESENTATION_EXECUTOR.schedule(() -> {
            nextRoundScheduled.set(false);
            if (!waitingFaintSlots.isEmpty() || !isBattleStillActive()) {
                return;
            }
            startNextRound();
        }, SWITCH_PRESENTATION_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private boolean isBattleStillActive() {
        for (FactionData faction : debutFactions) {
            if (faction.getFactionStatType() != FactionResultType.IN_BATTLE) {
                return false;
            }
        }
        return true;
    }

    private static byte toSelectorData(byte factionIndex, byte slotIndex) {
        return (byte) ((factionIndex & 0x0F) | ((slotIndex & 0x0F) << 4));
    }
}
