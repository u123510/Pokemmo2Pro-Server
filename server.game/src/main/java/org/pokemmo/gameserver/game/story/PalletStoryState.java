package org.pokemmo.gameserver.game.story;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.IntConsumer;

import org.pokemmo.gameserver.game.battle.BattleManager;
import org.pokemmo.gameserver.game.entity.NpcEntity;
import org.pokemmo.gameserver.game.trainer.TrainerTeamData;

/** Transient per-character scene. Checkpoints live in the database, not in this object. */
public final class PalletStoryState {
    long generation;
    byte expectedReply;
    boolean yesNo;
    boolean awaitingMap;
    IntConsumer continuation;
    ScheduledFuture<?> timer;
    volatile BattleManager battle;
    volatile Map<Long, NpcEntity> actors = Map.of();
    volatile PalletStoryProgress progress;
    volatile boolean progressLoadFailed;
    volatile Short parcelStatus;
    volatile Boolean viridianCatchComplete;
    volatile BattleManager viridianCatchBattle;
    volatile boolean nurseInteraction;
    volatile String activeStoryId;
    volatile String activeNodeId;
    volatile long interactionActorId = -1;
    volatile int interactionNpcIndex = -1;
    volatile String storyBattleId;
    volatile String storyBattleFinishNode;
    volatile String storyBattleResult;
    volatile int eliteFourStage;
    volatile Set<String> eventFlags = Set.of();
    volatile String normalTrainerBattleFlag;
    volatile TrainerTeamData pendingTrainerBattleDefinition;
    volatile String pendingTrainerBattleFlag;
    volatile String durableActionKey;

    public boolean hasEventFlag(String flag) {
        return flag != null && eventFlags.contains(flag);
    }

    public boolean isProgressLoadFailed() {
        return progressLoadFailed;
    }

    public void addEventFlag(String flag) {
        if (flag == null || flag.isBlank() || eventFlags.contains(flag)) return;
        java.util.Set<String> next = new java.util.HashSet<>(eventFlags);
        next.add(flag);
        eventFlags = Set.copyOf(next);
    }

    public void removeEventFlag(String flag) {
        if (flag == null || !eventFlags.contains(flag)) return;
        java.util.Set<String> next = new java.util.HashSet<>(eventFlags);
        next.remove(flag);
        eventFlags = Set.copyOf(next);
    }

    void setEliteFourStage(int stage) {
        if (stage < 0 || stage > 4) {
            throw new IllegalArgumentException("四天王阶段无效: " + stage);
        }
        java.util.Set<String> next = new java.util.HashSet<>(eventFlags);
        next.removeIf(flag -> flag.startsWith(
                org.pokemmo.gameserver.services.story.StoryEventFlagStore.ELITE_FOUR_STAGE_PREFIX));
        next.add(org.pokemmo.gameserver.services.story.StoryEventFlagStore.ELITE_FOUR_STAGE_PREFIX + stage);
        eventFlags = Set.copyOf(next);
        eliteFourStage = stage;
    }

    public String getNormalTrainerBattleFlag() {
        return normalTrainerBattleFlag;
    }

    public void setNormalTrainerBattleFlag(String flag) {
        normalTrainerBattleFlag = flag;
    }

    public TrainerTeamData getPendingTrainerBattleDefinition() {
        return pendingTrainerBattleDefinition;
    }

    public String getPendingTrainerBattleFlag() {
        return pendingTrainerBattleFlag;
    }

    public void setPendingTrainerBattle(TrainerTeamData definition, String flag) {
        pendingTrainerBattleDefinition = definition;
        pendingTrainerBattleFlag = flag;
    }

    public void clearPendingTrainerBattle() {
        pendingTrainerBattleDefinition = null;
        pendingTrainerBattleFlag = null;
    }

    void cancelWait() {
        generation++;
        continuation = null;
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
    }

    void clearStoryContext() {
        activeStoryId = null;
        activeNodeId = null;
        interactionActorId = -1;
        interactionNpcIndex = -1;
        storyBattleId = null;
        storyBattleFinishNode = null;
        storyBattleResult = null;
        durableActionKey = null;
        clearPendingTrainerBattle();
    }
}
