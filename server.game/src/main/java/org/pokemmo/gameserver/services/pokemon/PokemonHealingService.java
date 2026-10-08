package org.pokemmo.gameserver.services.pokemon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.move.MoveManager;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonStatusType;
import org.pokemmo.gameserver.util.ArrayUtil;

import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Restores HP/PP in one owner-scoped transaction, without modifying online objects. */
@Slf4j
public final class PokemonHealingService {
    private static final int PARTY_ID = PokemonContainerType.PARTY.getType();
    private final Database database;

    public PokemonHealingService(Database database) {
        this.database = database;
    }

    public record Result(boolean success, String reason, List<PokemonData> pokemons) {
        public Result {
            pokemons = List.copyOf(pokemons);
        }

        public static Result rejected(String reason) {
            return new Result(false, reason, List.of());
        }
    }

    public Result healParty(long characterId, PokemonData[] expectedParty) {
        return healParty(characterId, expectedParty, false);
    }

    public Result healParty(long characterId, PokemonData[] expectedParty, boolean cureStatus) {
        try {
            Map<Long, PokemonData> expected = validateParty(characterId, expectedParty);
            if (expected.isEmpty()) {
                return Result.rejected("当前队伍没有宝可梦");
            }
            return database.ctx().transactionResult(configuration -> {
                DSLContext transaction = DSL.using(configuration);
                List<PokemonRecord> records = transaction.selectFrom(POKEMON)
                        .where(POKEMON.TRAINER_ID.eq(characterId))
                        .and(POKEMON.CONTAINER_ID.eq(PARTY_ID))
                        .orderBy(POKEMON.ID.asc())
                        .forUpdate()
                        .fetch();
                if (records.size() != expected.size()) {
                    return Result.rejected("在线队伍与保存数据不一致，请重新登录后重试");
                }

                List<PokemonData> restored = new ArrayList<>();
                for (PokemonRecord record : records) {
                    PokemonData stored = new PokemonData.Builder().setByRecord(record).build();
                    if (!matchesPartyMember(expected.get(stored.getPokemonId()), stored)) {
                        return Result.rejected("队伍归属、槽位或属性已变化，请重新登录后重试");
                    }
                    if (restorePokemon(stored)) {
                        if (cureStatus) stored.setPokemonStatus(PokemonStatusType.NORMAL);
                        restored.add(stored);
                    }
                }
                if (restored.isEmpty()) {
                    return Result.rejected("当前队伍只有蛋，没有需要恢复的宝可梦");
                }

                // Validate the entire party before the first write; any failed write rolls everything back.
                for (PokemonData pokemon : restored) {
                    if (transaction.update(POKEMON)
                            .set(POKEMON.CURRENT_HP, pokemon.getCurrentHp())
                            .set(POKEMON.MOVES_PP, ArrayUtil.toShortObject(pokemon.getMovesPp()))
                            .where(POKEMON.ID.eq(pokemon.getPokemonId()))
                            .and(POKEMON.TRAINER_ID.eq(characterId))
                            .and(POKEMON.CONTAINER_ID.eq(PARTY_ID))
                            .and(POKEMON.CONTAINER_POSITION.eq(pokemon.getContainerPosition()))
                            .execute() != 1) {
                        throw new IllegalStateException("队伍恢复保存失败，整队事务回滚");
                    }
                    if (cureStatus && transaction.update(POKEMON)
                            .set(POKEMON.STATUS, (short) PokemonStatusType.NORMAL.getType())
                            .where(POKEMON.ID.eq(pokemon.getPokemonId()))
                            .and(POKEMON.TRAINER_ID.eq(characterId)).and(POKEMON.CONTAINER_ID.eq(PARTY_ID))
                            .execute() != 1) {
                        throw new IllegalStateException("队伍异常状态恢复保存失败");
                    }
                }
                return new Result(true, "", restored);
            });
        } catch (IllegalArgumentException exception) {
            log.warn("队伍恢复被拒绝: 角色编号={}, 原因={}", characterId, exception.getMessage());
            return Result.rejected(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("队伍恢复事务失败: 角色编号={}", characterId, exception);
            return Result.rejected("队伍恢复保存失败，请检查服务器日志");
        }
    }

    static Map<Long, PokemonData> validateParty(long characterId, PokemonData[] party) {
        if (characterId <= 0 || party == null || party.length != PokemonContainerType.PARTY.getSize()) {
            throw new IllegalArgumentException("角色编号或队伍容量无效");
        }
        Map<Long, PokemonData> expected = new HashMap<>();
        for (int slot = 0; slot < party.length; slot++) {
            PokemonData pokemon = party[slot];
            if (pokemon == null) {
                continue;
            }
            if (pokemon.getPokemonId() <= 0 || pokemon.getTrainerId() != characterId
                    || pokemon.getContainerId() != PARTY_ID || pokemon.getContainerPosition() != slot
                    || expected.put(pokemon.getPokemonId(), pokemon) != null) {
                throw new IllegalArgumentException("队伍存在非法归属、重复编号或错误槽位");
            }
        }
        return expected;
    }

    static boolean matchesPartyMember(PokemonData online, PokemonData stored) {
        return online != null && online.getPokemonId() == stored.getPokemonId()
                && online.getTrainerId() == stored.getTrainerId()
                && online.getContainerId() == stored.getContainerId()
                && online.getContainerPosition() == stored.getContainerPosition()
                && online.getPokemonIndexId() == stored.getPokemonIndexId()
                && online.getLevel() == stored.getLevel()
                && online.getPersonalityValue() == stored.getPersonalityValue()
                && online.getPpUpTimes() == stored.getPpUpTimes()
                && online.getEggValue() == stored.getEggValue()
                && Arrays.equals(online.getPokemonIvs(), stored.getPokemonIvs())
                && Arrays.equals(online.getPokemonEvs(), stored.getPokemonEvs())
                && Arrays.equals(online.getMoves(), stored.getMoves());
    }

    /** Only call on a detached copy. Status conditions and PP Up counts deliberately stay unchanged. */
    static boolean restorePokemon(PokemonData pokemon) {
        if ((pokemon.getEggValue() & 1) != 0) {
            return false;
        }
        if (pokemon.getMaxHp() <= 0 || pokemon.getMoves() == null || pokemon.getMoves().length != 4
                || pokemon.getMovesPp() == null || pokemon.getMovesPp().length != 4
                || pokemon.getCanRememberMoves() == null || pokemon.getCanRememberMoves().length != 4
                || pokemon.getOtName() == null) {
            throw new IllegalArgumentException("宝可梦的生命值或招式数据不完整");
        }
        short[] fullPp = new short[4];
        for (int slot = 0; slot < fullPp.length; slot++) {
            short moveId = pokemon.getMoves()[slot];
            if (moveId == 0) {
                continue;
            }
            if (moveId < 0 || MoveManager.getPokemonMove(moveId) == null) {
                throw new IllegalArgumentException("未找到招式资源: 招式编号=" + moveId);
            }
            fullPp[slot] = (short) Byte.toUnsignedInt(pokemon.getPokemonMoveMaxPp(slot));
        }
        pokemon.setCurrentHp(pokemon.getMaxHp());
        pokemon.setMovesPp(fullPp);
        return true;
    }
}
