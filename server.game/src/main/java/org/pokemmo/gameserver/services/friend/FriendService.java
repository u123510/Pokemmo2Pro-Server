package org.pokemmo.gameserver.services.friend;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.FriendListRecord;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.FRIEND_LIST;

/** Database boundary for the player's one-way friend relationships. */
@Slf4j
public final class FriendService {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_PACKET_ENTRIES = 0xFF;

    private final Database database;

    public FriendService(Database database) {
        this.database = database;
    }

    /** The fields needed by the client-side 0x63 friend list packet. */
    public record FriendEntry(long friendId, String friendName, int addTimeEpochSeconds) {
    }

    /** The result of toggling one relationship, where added=false means removed. */
    public record FriendActionResult(boolean success, boolean added, long friendId) {
        public static FriendActionResult rejected() {
            return new FriendActionResult(false, false, 0L);
        }
    }

    /** Resolves one unique character name to its persistent character ID. */
    public long findCharacterIdByName(String playerName) {
        if (!isValidName(playerName)) {
            return 0L;
        }
        try {
            CharacterRecord character = database.ctx().selectFrom(CHARACTER)
                    .where(DSL.lower(CHARACTER.NAME).eq(playerName.toLowerCase(Locale.ROOT)))
                    .fetchOne();
            Long characterId = character == null ? null : character.getId();
            return characterId == null || characterId <= 0 ? 0L : characterId;
        } catch (RuntimeException exception) {
            log.error("查询好友目标角色失败: target={}", playerName, exception);
            return 0L;
        }
    }

    /** Returns whether a one-way relationship is already persisted. */
    public boolean isFriend(long playerId, long friendId) {
        if (playerId <= 0 || friendId <= 0 || playerId == friendId) {
            return false;
        }
        try {
            return database.ctx().selectOne()
                    .from(FRIEND_LIST)
                    .where(FRIEND_LIST.PLAYER_ID.eq(playerId))
                    .and(FRIEND_LIST.FRIEND_ID.eq(friendId))
                    .fetchOne() != null;
        } catch (RuntimeException exception) {
            log.error("查询好友关系失败: playerId={}, friendId={}", playerId, friendId, exception);
            return false;
        }
    }

    /** Persists both directions after the target accepts a friend request. */
    public boolean addFriendPair(long firstPlayerId, long secondPlayerId) {
        if (firstPlayerId <= 0 || secondPlayerId <= 0 || firstPlayerId == secondPlayerId) {
            return false;
        }
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                List<CharacterRecord> characters = transaction.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.in(firstPlayerId, secondPlayerId))
                        .orderBy(CHARACTER.ID.asc())
                        .forUpdate()
                        .fetch();
                if (characters.size() != 2) {
                    return false;
                }

                LocalDateTime addTime = LocalDateTime.now(ZoneOffset.UTC);
                insertIfMissing(transaction, firstPlayerId, secondPlayerId, addTime);
                insertIfMissing(transaction, secondPlayerId, firstPlayerId, addTime);
                return true;
            });
        } catch (RuntimeException exception) {
            log.error("好友双向关系持久化失败: firstPlayerId={}, secondPlayerId={}",
                    firstPlayerId, secondPlayerId, exception);
            return false;
        }
    }

    /** Removes one direction; the client's remove action is one-way. */
    public boolean removeFriend(long playerId, long friendId) {
        if (playerId <= 0 || friendId <= 0 || playerId == friendId) {
            return false;
        }
        try {
            return database.ctx().deleteFrom(FRIEND_LIST)
                    .where(FRIEND_LIST.PLAYER_ID.eq(playerId))
                    .and(FRIEND_LIST.FRIEND_ID.eq(friendId))
                    .execute() == 1;
        } catch (RuntimeException exception) {
            log.error("删除好友关系失败: playerId={}, friendId={}", playerId, friendId, exception);
            return false;
        }
    }

    /**
     * Adds the target when no relationship exists, or removes it when it does.
     * The client uses the same C2S packet for both menu actions.
     */
    public FriendActionResult toggleFriend(long playerId, String targetPlayerName) {
        if (playerId <= 0 || !isValidName(targetPlayerName)) {
            return FriendActionResult.rejected();
        }

        String normalizedName = targetPlayerName.toLowerCase(Locale.ROOT);
        try {
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                List<CharacterRecord> candidates = transaction.selectFrom(CHARACTER)
                        .where(DSL.lower(CHARACTER.NAME).eq(normalizedName))
                        .fetch();
                if (candidates.size() != 1) {
                    log.warn("好友目标不存在或名称不唯一: playerId={}, target={}",
                            playerId, targetPlayerName);
                    return FriendActionResult.rejected();
                }

                CharacterRecord target = candidates.get(0);
                Long targetIdValue = target.getId();
                if (targetIdValue == null || targetIdValue <= 0 || targetIdValue == playerId) {
                    log.warn("拒绝添加自己为好友或目标角色 ID 无效: playerId={}, target={}",
                            playerId, targetPlayerName);
                    return FriendActionResult.rejected();
                }
                long targetId = targetIdValue;

                List<CharacterRecord> lockedCharacters = transaction.selectFrom(CHARACTER)
                        .where(CHARACTER.ID.in(playerId, targetId))
                        .orderBy(CHARACTER.ID.asc())
                        .forUpdate()
                        .fetch();
                if (lockedCharacters.size() != 2) {
                    log.warn("好友操作角色记录缺失: playerId={}, targetId={}", playerId, targetId);
                    return FriendActionResult.rejected();
                }

                FriendListRecord existing = transaction.selectFrom(FRIEND_LIST)
                        .where(FRIEND_LIST.PLAYER_ID.eq(playerId))
                        .and(FRIEND_LIST.FRIEND_ID.eq(targetId))
                        .forUpdate()
                        .fetchOne();
                if (existing != null) {
                    int deleted = transaction.deleteFrom(FRIEND_LIST)
                            .where(FRIEND_LIST.PLAYER_ID.eq(playerId))
                            .and(FRIEND_LIST.FRIEND_ID.eq(targetId))
                            .execute();
                    return deleted == 1
                            ? new FriendActionResult(true, false, targetId)
                            : FriendActionResult.rejected();
                }

                int inserted = transaction.insertInto(FRIEND_LIST)
                        .set(FRIEND_LIST.PLAYER_ID, playerId)
                        .set(FRIEND_LIST.FRIEND_ID, targetId)
                        .set(FRIEND_LIST.ADD_TIME, LocalDateTime.now(ZoneOffset.UTC))
                        .execute();
                return inserted == 1
                        ? new FriendActionResult(true, true, targetId)
                        : FriendActionResult.rejected();
            });
        } catch (RuntimeException exception) {
            log.error("好友关系持久化失败: playerId={}, target={}",
                    playerId, targetPlayerName, exception);
            return FriendActionResult.rejected();
        }
    }

    /** Loads the persisted list in a stable order and joins the current name. */
    public List<FriendEntry> getFriendList(long playerId) {
        if (playerId <= 0) {
            return List.of();
        }
        try {
            var rows = database.ctx()
                    .select(FRIEND_LIST.FRIEND_ID, CHARACTER.NAME, FRIEND_LIST.ADD_TIME)
                    .from(FRIEND_LIST)
                    .join(CHARACTER).on(FRIEND_LIST.FRIEND_ID.eq(CHARACTER.ID))
                    .where(FRIEND_LIST.PLAYER_ID.eq(playerId))
                    .orderBy(FRIEND_LIST.ADD_TIME.asc(), FRIEND_LIST.FRIEND_ID.asc())
                    .limit(MAX_PACKET_ENTRIES)
                    .fetch();
            return rows.map(row -> new FriendEntry(
                    valueOrZero(row.get(FRIEND_LIST.FRIEND_ID)),
                    valueOrEmpty(row.get(CHARACTER.NAME)),
                    toEpochSeconds(row.get(FRIEND_LIST.ADD_TIME))));
        } catch (RuntimeException exception) {
            log.error("查询好友列表失败: playerId={}", playerId, exception);
            return List.of();
        }
    }

    private static boolean isValidName(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_NAME_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static void insertIfMissing(
            DSLContext transaction, long playerId, long friendId, LocalDateTime addTime) {
        FriendListRecord existing = transaction.selectFrom(FRIEND_LIST)
                .where(FRIEND_LIST.PLAYER_ID.eq(playerId))
                .and(FRIEND_LIST.FRIEND_ID.eq(friendId))
                .forUpdate()
                .fetchOne();
        if (existing == null) {
            transaction.insertInto(FRIEND_LIST)
                    .set(FRIEND_LIST.PLAYER_ID, playerId)
                    .set(FRIEND_LIST.FRIEND_ID, friendId)
                    .set(FRIEND_LIST.ADD_TIME, addTime)
                    .execute();
        }
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int toEpochSeconds(LocalDateTime value) {
        if (value == null) {
            return 0;
        }
        long epochSeconds = value.toEpochSecond(ZoneOffset.UTC);
        if (epochSeconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (epochSeconds < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) epochSeconds;
    }
}
