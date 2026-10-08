package org.pokemmo.gameserver.services.story;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;

import static org.pokemmo.db.jooq.Tables.CHARACTER;

/** Owner-scoped persistence for generic Kanto story and badge milestones. */
public final class StoryProgressStore {
    public record Result(short value, boolean changed) {
    }

    private final Database database;

    public StoryProgressStore(Database database) {
        this.database = database;
    }

    public Result setKantoStoryBit(int accountId, long characterId, int bit) {
        return updateBit(accountId, characterId, bit, false);
    }

    public Result setKantoExtraStoryBit(int accountId, long characterId, int bit) {
        if (accountId <= 0 || characterId <= 0 || bit < 0 || bit > 15) {
            throw new IllegalArgumentException("扩展剧情标记参数无效");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = tx.selectFrom(CHARACTER)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .forUpdate()
                    .fetchOne();
            if (owner == null) {
                throw new IllegalStateException("角色不存在或不属于当前账号");
            }
            Short[] values = owner.getStoryLineFlag().clone();
            if (values.length < 2 || values[1] == null) {
                throw new IllegalStateException("扩展剧情数组无效");
            }
            short current = values[1];
            short next = (short) (current | (1 << bit));
            if (next == current) {
                return new Result(current, false);
            }
            values[1] = next;
            int changed = tx.update(CHARACTER)
                    .set(CHARACTER.STORY_LINE_FLAG, values)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute();
            if (changed != 1) {
                throw new IllegalStateException("扩展剧情标记保存失败");
            }
            return new Result(next, true);
        });
    }

    public Result setKantoBadgeBit(int accountId, long characterId, int bit) {
        return updateBit(accountId, characterId, bit, true);
    }

    public boolean setKantoChampion(int accountId, long characterId) {
        if (accountId <= 0 || characterId <= 0) {
            throw new IllegalArgumentException("冠军标志参数无效");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = tx.selectFrom(CHARACTER)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .forUpdate()
                    .fetchOne();
            if (owner == null) {
                throw new IllegalStateException("角色不存在或不属于当前账号");
            }
            Boolean[] values = owner.getChampionFlag().clone();
            if (values.length == 0 || values[0] == null) {
                throw new IllegalStateException("关都冠军存档数组无效");
            }
            if (values[0]) {
                return false;
            }
            values[0] = true;
            int changed = tx.update(CHARACTER)
                    .set(CHARACTER.CHAMPION_FLAG, values)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute();
            if (changed != 1) {
                throw new IllegalStateException("冠军标志保存失败");
            }
            return true;
        });
    }

    private Result updateBit(int accountId, long characterId, int bit, boolean badge) {
        if (accountId <= 0 || characterId <= 0 || bit < 0 || bit > 15) {
            throw new IllegalArgumentException("剧情标记参数无效");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            CharacterRecord owner = tx.selectFrom(CHARACTER)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .forUpdate()
                    .fetchOne();
            if (owner == null) {
                throw new IllegalStateException("角色不存在或不属于当前账号");
            }
            Short[] values = (badge ? owner.getBadgeFlag() : owner.getStoryLineFlag()).clone();
            if (values.length == 0 || values[0] == null) {
                throw new IllegalStateException("关都剧情数组无效");
            }
            short current = values[0];
            short next = (short) (current | (1 << bit));
            if (next == current) {
                return new Result(current, false);
            }
            values[0] = next;
            int changed = tx.update(CHARACTER)
                    .set(badge ? CHARACTER.BADGE_FLAG : CHARACTER.STORY_LINE_FLAG, values)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute();
            if (changed != 1) {
                throw new IllegalStateException("剧情标记保存失败");
            }
            return new Result(next, true);
        });
    }
}
