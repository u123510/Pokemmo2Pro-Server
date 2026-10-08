package org.pokemmo.chatserver.services;

import lombok.RequiredArgsConstructor;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.AccountRecord;


import static org.pokemmo.db.jooq.Tables.ACCOUNT;
import static org.pokemmo.db.jooq.Tables.CHARACTER;
@RequiredArgsConstructor
public class ChatServerService {
    private final Database database;
    public AccountRecord getAccountByCharacterId(long characterId) {
        return database.ctx()
                .select()
                .from(ACCOUNT)
                .join(CHARACTER).on(ACCOUNT.ACCOUNT_ID.eq(CHARACTER.ACCOUNT_ID))
                .where(CHARACTER.ID.eq(characterId))
                .fetchOneInto(AccountRecord.class);
  }


}
