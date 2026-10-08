package org.pokemmo.gameserver.services.story;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.story.PalletStoryProgress;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.POKEMON;
import static org.pokemmo.db.jooq.Tables.POKEMON_DEX;

/** Owner-scoped, atomic opening checkpoints and rewards. Never publishes uncommitted online state. */
public final class PalletStoryStore {
    public record Result(PalletStoryProgress progress, PokemonData starter, int money) { }
    private final Database database;

    public PalletStoryStore(Database database) {
        this.database = database;
    }

    /** Read the latest committed story fields for this account's character, without touching assets. */
    public PalletStoryProgress loadProgress(int accountId, long characterId) {
        if (accountId <= 0 || characterId <= 0) throw new IllegalArgumentException("角色归属无效");
        var stored = database.ctx().select(CHARACTER.OAK_LAB_STATUS, CHARACTER.FIRST_PARTNER_STATUS)
                .from(CHARACTER).where(CHARACTER.ID.eq(characterId))
                .and(CHARACTER.ACCOUNT_ID.eq(accountId)).fetchOne();
        if (stored == null) throw new IllegalStateException("角色不存在或不属于当前账号");
        return progress(stored.value1(), stored.value2());
    }

    public PalletStoryProgress advance(int accountId, long characterId, short expected, short next) {
        if (!((expected == 0 && next == 1) || (expected == 1 && next == 2))) {
            throw new IllegalArgumentException("不允许的剧情阶段变更");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = lockOwner(tx, accountId, characterId);
            PalletStoryProgress progress = progress(owner);
            progress.validateOpeningCheckpoint();
            if (progress.stage() >= next) return progress;
            if (progress.stage() != expected) {
                throw new IllegalStateException("剧情阶段与当前步骤不一致: 期望阶段=" + expected
                        + ", 目标阶段=" + next + ", 数据库阶段=" + progress.stage()
                        + ", 数据库初始选择=" + progress.starter() + "。请完整退出角色后重新登录读取存档");
            }
            saveStage(tx, accountId, characterId, expected, next);
            return new PalletStoryProgress(next, progress.starter());
        });
    }

    public Result claimStarter(int accountId, long characterId, short choice, PokemonData candidate) {
        if (choice < 0 || choice > 2 || candidate == null || candidate.getTrainerId() != characterId
                || candidate.getOriginalTrainerId() != characterId || candidate.getPokemonId() <= 0
                || candidate.getPokemonIndexId() != new int[]{1, 4, 7}[choice]) {
            throw new IllegalArgumentException("初始宝可梦领取参数无效");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = lockOwner(tx, accountId, characterId);
            PalletStoryProgress progress = progress(owner);
            if (progress.stage() >= PalletStoryProgress.RIVAL) return new Result(progress, null, owner.getMoney());
            progress.validateOpeningCheckpoint();
            if (progress.stage() != PalletStoryProgress.CHOOSE || progress.starter() != 3) {
                throw new IllegalStateException("当前剧情不能领取初始宝可梦");
            }
            List<PokemonRecord> party = tx.selectFrom(POKEMON)
                    .where(POKEMON.TRAINER_ID.eq(characterId)).and(POKEMON.CONTAINER_ID.eq(1))
                    .orderBy(POKEMON.ID).forUpdate().fetch();
            Set<Short> slots = new HashSet<>();
            for (PokemonRecord pokemon : party) {
                short slot = pokemon.getContainerPosition();
                if (slot < 0 || slot >= 6 || !slots.add(slot)) {
                    throw new IllegalStateException("队伍槽位异常，未发放宝可梦");
                }
            }
            short slot = 0;
            while (slot < 6 && slots.contains(slot)) slot++;
            if (slot == 6) throw new IllegalStateException("队伍已满，请先空出一个位置");
            candidate.setContainerId(1);
            candidate.setContainerPosition(slot);
            if (tx.insertInto(POKEMON).set(candidate.toPokemonRecord()).execute() != 1) {
                throw new IllegalStateException("初始宝可梦保存失败");
            }
            Short[] partners = owner.getFirstPartnerStatus().clone();
            partners[0] = choice;
            if (tx.update(CHARACTER).set(CHARACTER.FIRST_PARTNER_STATUS, partners)
                    .set(CHARACTER.OAK_LAB_STATUS, PalletStoryProgress.RIVAL)
                    .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .and(CHARACTER.OAK_LAB_STATUS.eq(PalletStoryProgress.CHOOSE)).execute() != 1) {
                throw new IllegalStateException("初始宝可梦领取进度保存失败");
            }
            updateDex(tx, characterId, candidate.getPokemonIndexId());
            return new Result(new PalletStoryProgress(PalletStoryProgress.RIVAL, choice), candidate, owner.getMoney());
        });
    }

    /** Called only after a server-resolved opening battle; repeated calls cannot pay twice. */
    public Result finishBattle(int accountId, long characterId, short choice, List<PokemonRecord> party, boolean won) {
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = lockOwner(tx, accountId, characterId);
            PalletStoryProgress progress = progress(owner);
            if (progress.completed()) return new Result(progress, null, owner.getMoney());
            if (progress.stage() != PalletStoryProgress.RIVAL || progress.starter() != choice
                    || party == null || party.isEmpty() || party.size() > 6) {
                throw new IllegalStateException("首次战斗结算与剧情进度不一致");
            }
            Set<Long> ids = new HashSet<>();
            Set<Short> slots = new HashSet<>();
            for (PokemonRecord pokemon : party) {
                if (pokemon.getTrainerId() != characterId || pokemon.getContainerId() != 1
                        || pokemon.getContainerPosition() < 0 || pokemon.getContainerPosition() >= 6
                        || !ids.add(pokemon.getId()) || !slots.add(pokemon.getContainerPosition())) {
                    throw new IllegalStateException("首次战斗结算包含非法队伍成员");
                }
                if (tx.update(POKEMON).set(POKEMON.CURRENT_HP, pokemon.getCurrentHp())
                        .set(POKEMON.MOVES_PP, pokemon.getMovesPp()).set(POKEMON.STATUS, pokemon.getStatus())
                        .set(POKEMON.LEVEL_VALUE, pokemon.getLevelValue()).set(POKEMON.EXP, pokemon.getExp())
                        .set(POKEMON.EV_VALUES, pokemon.getEvValues())
                        .where(POKEMON.ID.eq(pokemon.getId())).and(POKEMON.TRAINER_ID.eq(characterId))
                        .and(POKEMON.CONTAINER_ID.eq(1))
                        .and(POKEMON.CONTAINER_POSITION.eq(pokemon.getContainerPosition())).execute() != 1) {
                    throw new IllegalStateException("首次战斗队伍保存失败");
                }
            }
            int money = Math.addExact(owner.getMoney(), won ? 80 : 0);
            if (tx.update(CHARACTER).set(CHARACTER.OAK_LAB_STATUS, PalletStoryProgress.COMPLETE)
                    .set(CHARACTER.MONEY, money)
                    .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .and(CHARACTER.OAK_LAB_STATUS.eq(PalletStoryProgress.RIVAL)).execute() != 1) {
                throw new IllegalStateException("开场完成标记保存失败");
            }
            return new Result(new PalletStoryProgress(PalletStoryProgress.COMPLETE, choice), null, money);
        });
    }

    private static CharacterRecord lockOwner(DSLContext tx, int accountId, long characterId) {
        if (accountId <= 0 || characterId <= 0) throw new IllegalArgumentException("角色归属无效");
        CharacterRecord owner = tx.selectFrom(CHARACTER).where(CHARACTER.ID.eq(characterId))
                .and(CHARACTER.ACCOUNT_ID.eq(accountId)).forUpdate().fetchOne();
        if (owner == null) throw new IllegalStateException("角色不存在或不属于当前账号");
        return owner;
    }

    private static PalletStoryProgress progress(CharacterRecord owner) {
        return progress(owner.getOakLabStatus(), owner.getFirstPartnerStatus());
    }

    private static PalletStoryProgress progress(Short stage, Short[] partners) {
        if (stage == null || partners == null || partners.length != 5 || partners[0] == null) {
            throw new IllegalStateException("角色初始宝可梦存档无效");
        }
        return new PalletStoryProgress(stage, partners[0]);
    }

    private static void saveStage(DSLContext tx, int accountId, long characterId, short expected, short next) {
        if (tx.update(CHARACTER).set(CHARACTER.OAK_LAB_STATUS, next)
                .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                .and(CHARACTER.OAK_LAB_STATUS.eq(expected)).execute() != 1) {
            throw new IllegalStateException("剧情检查点保存失败");
        }
    }

    private static void updateDex(DSLContext tx, long characterId, int species) {
        var record = tx.selectFrom(POKEMON_DEX).where(POKEMON_DEX.PLAYER_ID.eq(characterId)).forUpdate().fetchOne();
        BitSet met = record == null ? new BitSet() : BitSet.valueOf(record.getMeetLevel());
        BitSet owned = record == null ? new BitSet() : BitSet.valueOf(record.getAlreadyHaveLevel());
        met.set(species);
        owned.set(species);
        if (record == null) {
            if (tx.insertInto(POKEMON_DEX).set(POKEMON_DEX.PLAYER_ID, characterId)
                    .set(POKEMON_DEX.MEET_LEVEL, met.toByteArray())
                    .set(POKEMON_DEX.ALREADY_HAVE_LEVEL, owned.toByteArray())
                    .set(POKEMON_DEX.CAUGHT_LEVEL, new byte[0])
                    .set(POKEMON_DEX.CAUGHT_ALPHA_LEVEL, new byte[0]).execute() != 1) {
                throw new IllegalStateException("初始宝可梦图鉴保存失败");
            }
        } else if (tx.update(POKEMON_DEX).set(POKEMON_DEX.MEET_LEVEL, met.toByteArray())
                .set(POKEMON_DEX.ALREADY_HAVE_LEVEL, owned.toByteArray())
                .where(POKEMON_DEX.PLAYER_ID.eq(characterId)).execute() != 1) {
            throw new IllegalStateException("初始宝可梦图鉴更新失败");
        }
    }
}
