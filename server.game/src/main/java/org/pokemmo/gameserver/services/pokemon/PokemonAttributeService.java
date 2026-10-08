package org.pokemmo.gameserver.services.pokemon;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.ContainerRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.particleEffectType.ParticleEffectType;
import org.pokemmo.gameserver.game.pokemon.PokemonData;
import org.pokemmo.gameserver.game.pokemon.PokemonNormalRibbonType;
import org.pokemmo.gameserver.game.pokemon.PokemonRibbonMask;
import org.pokemmo.gameserver.services.GameServerService.PokemonPositionChange;
import org.pokemmo.gameserver.services.character.CharacterService;
import org.pokemmo.gameserver.services.world.WorldService;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.POKEMON;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;

import static org.pokemmo.gameserver.services.pokemon.PokemonServiceStore.*;

/** Validates and persists Pokemon attributes and ribbon state. */
@Slf4j
final class PokemonAttributeService {
    private final Database database;

    PokemonAttributeService(Database database) {
        this.database = database;
    }

public void addPokemon(PokemonRecord pokemon) {
    database.ctx()
            .insertInto(POKEMON)
            .set(pokemon)
            .execute();
  }

  public boolean updatePokemonAlpha(long characterId, long pokemonId, boolean isAlpha) {
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.IS_ALPHA, isAlpha)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonShiny(long characterId, long pokemonId, boolean isShiny) {
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.IS_SHINY, isShiny)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonSecret(long characterId, long pokemonId, boolean isSecret) {
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.IS_SECRET, isSecret)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonPersonalityValue(long characterId, long pokemonId, int personalityValue) {
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.PERSONALITY_VALUE, personalityValue)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonOtName(long characterId, long pokemonId, String otName) {
    if (otName == null || otName.isBlank() || otName.length() > 32) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.OT_NAME, otName)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonName(long characterId, long pokemonId, String name) {
    if (name == null || name.length() > 16 || !isValidPokemonName(name)) {
      return false;
    }
    try {
      return database.ctx()
              .update(POKEMON)
              .set(POKEMON.NAME, name)
              .where(POKEMON.ID.eq(pokemonId))
              .and(POKEMON.TRAINER_ID.eq(characterId))
              .execute() > 0;
    } catch (RuntimeException exception) {
      log.error("保存精灵昵称失败: characterId={}, pokemonId={}, name={}",
              characterId, pokemonId, name, exception);
      return false;
    }
  }

  private boolean isValidPokemonName(String name) {
    for (int index = 0; index < name.length(); index++) {
      char character = name.charAt(index);
      if (!((character >= 'a' && character <= 'z')
              || (character >= 'A' && character <= 'Z')
              || (character >= '0' && character <= '9')
              || character == ' ' || character == ',' || character == '.'
              || character == '!' || character == '?' || character == '"'
              || character == '\'' || character == '-' || character == '~'
              || (character >= '\u00C0' && character <= '\u00FF'
              && character != '\u00D7' && character != '\u00F7'))) {
        return false;
      }
    }
    return true;
  }

  public boolean updatePokemonNormalRibbons(long characterId, long pokemonId, boolean[] normalRibbons) {
    if (normalRibbons == null || normalRibbons.length != PokemonNormalRibbonType.values().length) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.NORMAL_RIBBON, org.pokemmo.gameserver.util.ArrayUtil.toBooleanObject(normalRibbons))
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonRibbons(
          long characterId,
          long pokemonId,
          short[] contestRibbons,
          boolean[] normalRibbons) {
    try {
      PokemonRibbonMask.encode(contestRibbons, normalRibbons);
    } catch (IllegalArgumentException exception) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.CONTEST_RIBBON, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(contestRibbons))
            .set(POKEMON.NORMAL_RIBBON, org.pokemmo.gameserver.util.ArrayUtil.toBooleanObject(normalRibbons))
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonBallType(long characterId, long pokemonId, short ballType) {
    if (ballType < 0 || ballType > 24) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.BALL_TYPE, ballType)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonParticleEffects(
          long characterId, long pokemonId, short[] particleEffects) {
    if (particleEffects == null || particleEffects.length > ParticleEffectType.values().length) {
      return false;
    }
    Set<Short> uniqueParticleEffects = new HashSet<>();
    for (short particleEffect : particleEffects) {
      if (particleEffect < 0 || particleEffect > 38
              || ParticleEffectType.getByType(particleEffect) == null
              || !uniqueParticleEffects.add(particleEffect)) {
        return false;
      }
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.PARTICLE_EFFECTS, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(particleEffects))
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonCurrentSelectParticleEffect(
          long characterId, long pokemonId, short particleEffectId) {
    short normalizedParticleEffectId = particleEffectId == UNSIGNED_RANDOM_PARTICLE_EFFECT
            ? RANDOM_PARTICLE_EFFECT : particleEffectId;
    if (normalizedParticleEffectId != -1 && normalizedParticleEffectId != RANDOM_PARTICLE_EFFECT
            && (normalizedParticleEffectId < 0 || normalizedParticleEffectId > 38
                    || ParticleEffectType.getByType(normalizedParticleEffectId) == null)) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.CURRENT_SELECT_PARTICLE_EFFECT_TYPE, normalizedParticleEffectId)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonIvs(long characterId, long pokemonId, short[] ivValues) {
    if (ivValues == null || ivValues.length != 6) {
      return false;
    }
    for (short ivValue : ivValues) {
      if (ivValue < 0 || ivValue > 31) {
        return false;
      }
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.IV_VALUES, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(ivValues))
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonHiddenAbility(long characterId, long pokemonId, boolean hasHiddenAbility) {
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.HAS_HIDDEN_ABILITY, hasHiddenAbility)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonAbilityIndex(long characterId, long pokemonId, short abilityIndex) {
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.ABILITY, abilityIndex)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonEvs(long characterId, long pokemonId, short[] evValues) {
    if (evValues == null || evValues.length != 6) {
      return false;
    }
    for (short evValue : evValues) {
      if (evValue < 0 || evValue > 252) {
        return false;
      }
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.EV_VALUES, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(evValues))
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  public boolean updatePokemonFriendValue(long characterId, long pokemonId, short friendValue) {
    if (friendValue < 0 || friendValue > 255) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.FRIEND_VALUE, friendValue)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  /**
   * 更新宝可梦形态编号（0..255）。
   * 采用 pokemonId 与 trainerId 双重条件执行持久化，防止跨角色越权写入。
   */
  public boolean updatePokemonFormType(long characterId, long pokemonId, short formType) {
    if (formType < 0 || formType > 255) {
      log.warn("更新宝可梦形态失败，形态值超出有效范围: characterId={}, pokemonId={}, formType={}",
              characterId, pokemonId, formType);
      return false;
    }
    try {
      boolean success = database.ctx()
              .update(POKEMON)
              .set(POKEMON.FORM_TYPE, formType)
              .where(POKEMON.ID.eq(pokemonId))
              .and(POKEMON.TRAINER_ID.eq(characterId))
              .execute() > 0;
      if (success) {
        log.info("更新宝可梦形态成功: characterId={}, pokemonId={}, formType={}",
                characterId, pokemonId, formType);
      } else {
        log.warn("更新宝可梦形态未匹配到记录: characterId={}, pokemonId={}, formType={}",
                characterId, pokemonId, formType);
      }
      return success;
    } catch (RuntimeException exception) {
      log.error("保存宝可梦形态异常: characterId={}, pokemonId={}, formType={}",
              characterId, pokemonId, formType, exception);
      return false;
    }
  }

  /**
   * 更新宝可梦对战成长数据（经验值、等级与努力值）。
   * 采用 pokemonId 与 trainerId 双重条件执行持久化，防止跨角色越权写入。
   */
  public boolean updatePokemonGrowth(long characterId, long pokemonId, int exp, short level, short[] evValues) {
    if (level < 1 || level > 100 || exp < 0) {
      return false;
    }
    var step = database.ctx()
            .update(POKEMON)
            .set(POKEMON.EXP, exp)
            .set(POKEMON.LEVEL_VALUE, level);
    if (evValues != null && evValues.length == 6) {
      step.set(POKEMON.EV_VALUES, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(evValues));
    }
    return step.where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }

  /**
   * 更新宝可梦的配招、技能PP与PP提升次数。
   * 采用 pokemonId 与 trainerId 双重条件执行持久化，防止跨角色越权写入。
   */
  public boolean updatePokemonMoves(long characterId, long pokemonId,
                                    short[] moves, short[] movesPp, byte ppUpTimes) {
    if (moves == null || moves.length != 4 || movesPp == null || movesPp.length != 4) {
      return false;
    }
    return database.ctx()
            .update(POKEMON)
            .set(POKEMON.MOVES, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(moves))
            .set(POKEMON.MOVES_PP, org.pokemmo.gameserver.util.ArrayUtil.toShortObject(movesPp))
            .set(POKEMON.PP_UP_TIMES, (short) ppUpTimes)
            .where(POKEMON.ID.eq(pokemonId))
            .and(POKEMON.TRAINER_ID.eq(characterId))
            .execute() > 0;
  }
}
