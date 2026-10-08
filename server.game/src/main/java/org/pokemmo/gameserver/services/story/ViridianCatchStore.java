package org.pokemmo.gameserver.services.story;

import java.util.List;

import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.story.ViridianCatchCatalog;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.INVENTORY;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

/** Account-scoped persistence for the Viridian capture tutorial flag. */
public final class ViridianCatchStore {
    public static final short STORY_LINE_MASK = 1 << 4;

    public record Result(boolean complete, short kantoStoryFlags, boolean changed) {
    }

    private final Database database;

    public ViridianCatchStore(Database database) {
        this.database = database;
    }

    public boolean loadComplete(int accountId, long characterId) {
        CharacterRecord character = findCharacter(accountId, characterId);
        return isComplete(kantoFlags(character));
    }

    public boolean hasCaptureBall(int accountId, long characterId) {
        findCharacter(accountId, characterId);
        List<Short> itemIds = database.ctx().select(OWNED_ITEM.ITEM_INDEX_ID)
                .from(OWNED_ITEM)
                .join(INVENTORY).on(OWNED_ITEM.INVENTORY_ID.eq(INVENTORY.ID))
                .where(OWNED_ITEM.OWNER_ID.eq(characterId))
                .and(INVENTORY.NAME.eq("inventory"))
                .and(OWNED_ITEM.ITEM_AMOUNT.gt((short) 0))
                .fetch(OWNED_ITEM.ITEM_INDEX_ID);
        return itemIds.stream().anyMatch(itemId -> itemId != null
                && itemId == ViridianCatchCatalog.BALL_ITEM_ID
                && ItemManager.getItemData(itemId) != null
                && ItemManager.getItemData(itemId).isCaptureBall());
    }

    public Result complete(int accountId, long characterId) {
        if (accountId <= 0 || characterId <= 0) {
            throw new IllegalArgumentException("捕获教学角色归属无效");
        }
        return database.ctx().transactionResult(configuration -> {
            var tx = org.jooq.impl.DSL.using(configuration);
            CharacterRecord character = tx.selectFrom(CHARACTER)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .forUpdate()
                    .fetchOne();
            if (character == null) {
                throw new IllegalStateException("角色不存在或不属于当前账号");
            }
            short current = kantoFlags(character);
            if (isComplete(current)) {
                return new Result(true, current, false);
            }
            Short[] flags = character.getStoryLineFlag().clone();
            flags[0] = (short) (current | STORY_LINE_MASK);
            if (tx.update(CHARACTER).set(CHARACTER.STORY_LINE_FLAG, flags)
                    .where(CHARACTER.ID.eq(characterId))
                    .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute() != 1) {
                throw new IllegalStateException("捕获教学完成标记保存失败");
            }
            return new Result(true, flags[0], true);
        });
    }

    static boolean isComplete(short storyFlags) {
        return (storyFlags & STORY_LINE_MASK) != 0;
    }

    static short withComplete(short storyFlags) {
        return (short) (storyFlags | STORY_LINE_MASK);
    }

    private CharacterRecord findCharacter(int accountId, long characterId) {
        if (accountId <= 0 || characterId <= 0) {
            throw new IllegalArgumentException("捕获教学角色归属无效");
        }
        CharacterRecord character = database.ctx().selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(characterId))
                .and(CHARACTER.ACCOUNT_ID.eq(accountId))
                .fetchOne();
        if (character == null) {
            throw new IllegalStateException("角色不存在或不属于当前账号");
        }
        return character;
    }

    private static short kantoFlags(CharacterRecord character) {
        Short[] flags = character.getStoryLineFlag();
        if (flags == null || flags.length != 5 || flags[0] == null) {
            throw new IllegalStateException("角色故事线存档无效");
        }
        return flags[0];
    }
}
