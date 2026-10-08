package org.pokemmo.gameserver.services.character;

import org.jooq.Record;
import org.jooq.Result;
import org.jooq.TableField;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.gameserver.game.character.CharacterData;
import org.pokemmo.gameserver.game.skin.SkinType;

import java.util.List;

import static org.pokemmo.db.jooq.Tables.CHARACTER;

public final class CharacterService {
    private final Database database;

    public record SafariStart(boolean success, int remainingMoney, short steps, short balls) {
    }

    public CharacterService(Database database) {
        this.database = database;
    }

public List<CharacterData> getCharacters(int accountId) {
    Result<Record> accountCharacters = database.ctx()
        .select().from(CHARACTER)
        .where(CHARACTER.ACCOUNT_ID.eq(accountId))
        .fetch();
    return accountCharacters.map(character -> character.into(CHARACTER))
            .stream()
            .map(character -> new CharacterData.Builder()
            .setByRecord(character)
                    .build())
            .toList();
  }

public CharacterData getCharacter(long characterId) {
    Record characterRecord = database.ctx()
        .select().from(CHARACTER)
        .where(CHARACTER.ID.eq(characterId))
        .fetchOne();
      if (characterRecord != null) {
          return  new CharacterData.Builder()
                  .setByRecord(characterRecord.into(CHARACTER))
                  .build();
      }
      return null;
  }

public CharacterData getCharacterByName(String characterName) {
    Record characterRecord = database.ctx()
            .select().from(CHARACTER)
            .where(CHARACTER.NAME.eq(characterName))
            .fetchOne();
    if (characterRecord != null) {
      return new CharacterData.Builder()
              .setByRecord(characterRecord.into(CHARACTER))
              .build();
    }
    return null;
  }

public void updateCharacter(CharacterRecord character) {
    if (character == null || character.getId() == null) {
      return;
    }
    database.ctx()
            .update(CHARACTER)
            .set(character)
            .where(CHARACTER.ID.eq(character.getId()))
            .execute();
  }

public SafariStart startSafari(long characterId, int fee, short steps, short balls) {
    if (characterId <= 0 || fee < 0 || steps <= 0 || balls <= 0) {
      return new SafariStart(false, -1, (short) 0, (short) 0);
    }
    return database.ctx().transactionResult(configuration -> {
      var tx = org.jooq.impl.DSL.using(configuration);
      CharacterRecord character = tx.selectFrom(CHARACTER)
          .where(CHARACTER.ID.eq(characterId))
          .forUpdate()
          .fetchOne();
      if (character == null || character.getMoney() == null || character.getMoney() < fee) {
        return new SafariStart(false, character == null ? -1 : character.getMoney(),
            (short) 0, (short) 0);
      }
      int remainingMoney = character.getMoney() - fee;
      int changed = tx.update(CHARACTER)
          .set(CHARACTER.MONEY, remainingMoney)
          .set(CHARACTER.SAFARI_STEPS, steps)
          .set(CHARACTER.SAFARI_BALL_AMOUNT, balls)
          .where(CHARACTER.ID.eq(characterId))
          .execute();
      if (changed != 1) {
        throw new IllegalStateException("野生原野区入场状态保存失败");
      }
      return new SafariStart(true, remainingMoney, steps, balls);
    });
  }

public boolean updateSafariState(long characterId, short steps, short balls) {
    if (characterId <= 0 || steps < 0 || balls < 0 || balls > 255) {
      return false;
    }
    return database.ctx()
        .update(CHARACTER)
        .set(CHARACTER.SAFARI_STEPS, steps)
        .set(CHARACTER.SAFARI_BALL_AMOUNT, balls)
        .where(CHARACTER.ID.eq(characterId))
        .execute() == 1;
  }

public boolean addOnlineMinutes(long characterId, int minutes) {
    if (characterId <= 0 || minutes <= 0) {
      return false;
    }
    int maxCurrentMinutes = Integer.MAX_VALUE - minutes;
    return database.ctx()
            .update(CHARACTER)
            .set(CHARACTER.ONLINE_MINUTES, CHARACTER.ONLINE_MINUTES.plus(minutes))
            .where(CHARACTER.ID.eq(characterId))
            .and(CHARACTER.ONLINE_MINUTES.ge(0))
            .and(CHARACTER.ONLINE_MINUTES.le(maxCurrentMinutes))
            .execute() == 1;
  }

public boolean updateCharacterFollower(long characterId, short pokemonIndexId, short rarity) {
    if (characterId <= 0 || pokemonIndexId < 0 || rarity < 0 || rarity > 0xFF) {
      return false;
    }
    return database.ctx()
        .update(CHARACTER)
        .set(CHARACTER.FOLLOWER_POKEMON_INDEX_ID, pokemonIndexId)
        .set(CHARACTER.FOLLOWER_POKEMON_RARITY, rarity)
        .where(CHARACTER.ID.eq(characterId))
        .execute() == 1;
  }

public boolean updateCharacterSkin(long characterId, SkinType skinType, short skin, short color) {
    if (characterId <= 0 || skinType == null || skin < -1 || skin > 1022 || color < -1 || color > 62) {
      return false;
    }
    if (skinType == SkinType.BIKE) {
      if (color != -1) {
        return false;
      }
      return database.ctx()
          .update(CHARACTER)
          .set(CHARACTER.BIKE, skin)
          .where(CHARACTER.ID.eq(characterId))
          .execute() == 1;
    }
    TableField<CharacterRecord, Short> skinField = null;
    TableField<CharacterRecord, Short> colorField = null;
    switch (skinType) {
      case FOREHEAD -> { skinField = CHARACTER.FOREHEAD; colorField = CHARACTER.FOREHEAD_COLOR; }
      case HAT -> { skinField = CHARACTER.HAT; colorField = CHARACTER.HAT_COLOR; }
      case HAIR -> { skinField = CHARACTER.HAIR; colorField = CHARACTER.HAIR_COLOR; }
      case EYES -> { skinField = CHARACTER.EYES; colorField = CHARACTER.EYES_COLOR; }
      case FACIAL_HAIR -> { skinField = CHARACTER.FACIAL_HAIR; colorField = CHARACTER.FACIAL_HAIR_COLOR; }
      case BACK -> { skinField = CHARACTER.BACK; colorField = CHARACTER.BACK_COLOR; }
      case TOP -> { skinField = CHARACTER.TOP; colorField = CHARACTER.TOP_COLOR; }
      case GLOVES -> { skinField = CHARACTER.GLOVES; colorField = CHARACTER.GLOVES_COLOR; }
      case FOOTWEAR -> { skinField = CHARACTER.FOOTWEAR; colorField = CHARACTER.FOOTWEAR_COLOR; }
      case LEGGINGS -> { skinField = CHARACTER.LEGGINGS; colorField = CHARACTER.LEGGINGS_COLOR; }
      case FISHING_ROD, BIKE -> { return false; }
    }
    if (skinField == null || colorField == null) {
      return false;
    }
    return database.ctx()
        .update(CHARACTER)
        .set(skinField, skin)
        .set(colorField, color)
        .where(CHARACTER.ID.eq(characterId))
        .execute() == 1;
  }

}
