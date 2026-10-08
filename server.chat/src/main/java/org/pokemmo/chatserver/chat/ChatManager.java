package org.pokemmo.chatserver.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.pokemmo.db.jooq.tables.records.AccountRecord;
@Getter @Setter @AllArgsConstructor
@Slf4j
public class ChatManager {
    //加入对话服务器的玩家账号信息
    private AccountRecord accountRecord;
    public static class Builder {
        private AccountRecord accountRecord;
        public Builder setAccountRecord(AccountRecord accountRecord) {
            this.accountRecord = accountRecord;
            return this;
        }
        public ChatManager build() {
            return new ChatManager(accountRecord);
        }
    }
}
