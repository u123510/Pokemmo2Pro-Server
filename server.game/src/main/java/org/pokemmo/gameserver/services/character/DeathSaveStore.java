package org.pokemmo.gameserver.services.character;

import com.google.inject.Inject;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.gameserver.game.character.DeathSavePoint;
import org.pokemmo.gameserver.game.character.DeathSavePointCatalog;
import org.pokemmo.gameserver.game.map.MapData;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.services.pokemon.PokemonHealingService;

import static org.pokemmo.db.jooq.Tables.CHARACTER;

/** Owner-scoped checkpoints; party restoration and return position share one transaction. */
public final class DeathSaveStore {
    private static final Table<Record> DEATH_SAVE = DSL.table(DSL.name("public", "death_save"));
    private static final Field<Long> PLAYER_ID = DSL.field(DSL.name("player_id"), Long.class);
    private static final Field<Short> UNOVA_POINT_ID = DSL.field(DSL.name("unova_point_id"), Short.class);
    private final Database database;
    private final PokemonHealingService healing;

    @Inject
    public DeathSaveStore(Database database) {
        this.database = database;
        this.healing = new PokemonHealingService(database);
    }

    public void save(long characterId, int accountId, DeathSavePoint point) {
        database.ctx().transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            lockCharacter(tx, characterId, accountId);
            writePoint(tx, characterId, point.id());
        });
    }

    public int load(long characterId, int accountId) {
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            lockCharacter(tx, characterId, accountId);
            return readPoint(tx, characterId);
        });
    }

    public PokemonHealingService.Result restore(long characterId, int accountId,
            DeathSavePointCatalog catalog, DeathSavePoint point, MapData destination,
            byte toward, PokemonData[] party) {
        if (destination == null || destination.getRegionIndexId() != 2
                || !point.destinationMap().equals(destination.getMapKey())
                || !destination.checkIsWalkable(point.x(), point.y())) {
            throw new IllegalArgumentException("合众回城目标地图或落点无效");
        }
        return database.ctx().transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            lockCharacter(tx, characterId, accountId);
            if (catalog.get(readPoint(tx, characterId)).id() != point.id()) {
                throw new IllegalStateException("合众存盘点在回城前发生变化，请重试");
            }
            PokemonHealingService.Result restored = healing.healParty(tx, characterId, party, true);
            if (!restored.success()) {
                throw new IllegalStateException(restored.reason());
            }
            if (tx.update(CHARACTER)
                    .set(CHARACTER.REGION_ID, (short) 2)
                    .set(CHARACTER.MAP_HEADER_ID_OR_GBA_MAP_GROUP_ID,
                            (short) Byte.toUnsignedInt(destination.getMapHeaderIdOrGBAmapGroupId()))
                    .set(CHARACTER.GBA_MAP_ID, (short) Byte.toUnsignedInt(destination.getGbaMapId()))
                    .set(CHARACTER.X, point.x()).set(CHARACTER.Y, point.y())
                    .set(CHARACTER.Z, (short) point.z()).set(CHARACTER.TOWARD, (short) toward)
                    .set(CHARACTER.TRANSPORTATION, (short) 0)
                    .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                    .execute() != 1) {
                throw new IllegalStateException("角色回城位置保存失败");
            }
            writePoint(tx, characterId, point.id());
            return restored;
        });
    }

    private static void lockCharacter(DSLContext tx, long characterId, int accountId) {
        if (characterId <= 0 || accountId <= 0) {
            throw new IllegalArgumentException("角色或账号编号无效");
        }
        CharacterRecord character = tx.selectFrom(CHARACTER)
                .where(CHARACTER.ID.eq(characterId)).and(CHARACTER.ACCOUNT_ID.eq(accountId))
                .forUpdate().fetchOne();
        if (character == null) throw new IllegalArgumentException("角色归属校验失败");
    }

    private static int readPoint(DSLContext tx, long characterId) {
        Short stored = tx.select(UNOVA_POINT_ID).from(DEATH_SAVE)
                .where(PLAYER_ID.eq(characterId)).fetchOne(UNOVA_POINT_ID);
        return stored == null ? DeathSavePointCatalog.HOME_ID : stored;
    }

    private static void writePoint(DSLContext tx, long characterId, int pointId) {
        if (pointId < 200 || pointId > 213) {
            throw new IllegalArgumentException("合众存盘点只能写入 200..213");
        }
        if (tx.insertInto(DEATH_SAVE).columns(PLAYER_ID, UNOVA_POINT_ID)
                .values(characterId, (short) pointId).onConflict(PLAYER_ID).doUpdate()
                .set(UNOVA_POINT_ID, (short) pointId).execute() != 1) {
            throw new IllegalStateException("合众存盘点保存失败");
        }
    }
}
