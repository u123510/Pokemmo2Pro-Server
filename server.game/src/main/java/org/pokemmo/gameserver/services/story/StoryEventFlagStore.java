package org.pokemmo.gameserver.services.story;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Durable per-character map-event flags used by generated Kanto pickups. */
public final class StoryEventFlagStore {
    public static final String ELITE_FOUR_STAGE_PREFIX = "kanto/elite_four/stage_";

    private final Database database;

    public StoryEventFlagStore(Database database) {
        this.database = database;
        database.ctx().execute("""
                CREATE TABLE IF NOT EXISTS character_story_event_flag (
                    character_id BIGINT NOT NULL,
                    flag_name VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (character_id, flag_name),
                    FOREIGN KEY (character_id) REFERENCES character(id) ON DELETE CASCADE
                )
                """);
    }

    public Set<String> load(long characterId) {
        if (characterId <= 0) return Set.of();
        return new HashSet<>(database.ctx().fetch(
                "SELECT flag_name FROM character_story_event_flag WHERE character_id = ?",
                characterId).getValues("flag_name", String.class));
    }

    public int loadEliteFourStage(long characterId) {
        int stage = 0;
        for (String flag : load(characterId)) {
            if (!flag.startsWith(ELITE_FOUR_STAGE_PREFIX)) continue;
            try {
                int candidate = Integer.parseInt(flag.substring(ELITE_FOUR_STAGE_PREFIX.length()));
                if (candidate >= 0 && candidate <= 4) {
                    stage = Math.max(stage, candidate);
                }
            } catch (NumberFormatException ignored) {
                // Ignore unrelated or malformed event flags.
            }
        }
        return stage;
    }

    public boolean setEliteFourStage(long characterId, int stage) {
        if (characterId <= 0 || stage < 0 || stage > 4) return false;
        String flag = ELITE_FOUR_STAGE_PREFIX + stage;
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute(
                    "DELETE FROM character_story_event_flag "
                            + "WHERE character_id = ? AND flag_name LIKE ?",
                    characterId, ELITE_FOUR_STAGE_PREFIX + "%");
            return tx.execute("""
                    INSERT INTO character_story_event_flag (character_id, flag_name)
                    VALUES (?, ?)
                    ON CONFLICT (character_id, flag_name)
                    DO UPDATE SET created_at = CURRENT_TIMESTAMP
                    """, characterId, flag) == 1;
        });
    }

    public boolean setEventFlag(long characterId, String flagName) {
        if (characterId <= 0 || flagName == null || flagName.isBlank()) return false;
        database.ctx().execute("""
                INSERT INTO character_story_event_flag (character_id, flag_name)
                VALUES (?, ?)
                ON CONFLICT (character_id, flag_name)
                DO UPDATE SET created_at = CURRENT_TIMESTAMP
                """, characterId, flagName);
        return true;
    }

    /** Compatibility alias for older callers. */
    public boolean set(long characterId, String flagName) {
        return setEventFlag(characterId, flagName);
    }

    public boolean clearEventFlag(long characterId, String flagName) {
        if (characterId <= 0 || flagName == null || flagName.isBlank()) return false;
        return database.ctx().execute(
                "DELETE FROM character_story_event_flag WHERE character_id = ? AND flag_name = ?",
                characterId, flagName) >= 0;
    }

    /** Compatibility alias for older callers. */
    public boolean clear(long characterId, String flagName) {
        return clearEventFlag(characterId, flagName);
    }

    public boolean cooldownElapsed(long characterId, String flagName, Duration cooldown) {
        if (cooldown == null || cooldown.isNegative()) return true;
        LocalDateTime createdAt = createdAt(characterId, flagName);
        return createdAt == null || !createdAt.plus(cooldown).isAfter(LocalDateTime.now());
    }

    public long remainingSeconds(long characterId, String flagName, Duration cooldown) {
        if (cooldown == null || cooldown.isNegative()) return 0;
        LocalDateTime createdAt = createdAt(characterId, flagName);
        if (createdAt == null) return 0;
        long seconds = Duration.between(LocalDateTime.now(), createdAt.plus(cooldown)).getSeconds();
        return Math.max(0, seconds);
    }

    private LocalDateTime createdAt(long characterId, String flagName) {
        if (characterId <= 0 || flagName == null || flagName.isBlank()) return null;
        var rows = database.ctx().fetch(
                "SELECT created_at FROM character_story_event_flag "
                        + "WHERE character_id = ? AND flag_name = ?",
                characterId, flagName);
        return rows.isEmpty() ? null : rows.get(0).get("created_at", LocalDateTime.class);
    }
}
