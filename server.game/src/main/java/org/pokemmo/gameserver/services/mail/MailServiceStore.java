package org.pokemmo.gameserver.services.mail;

import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.pokemmo.db.Database;
import org.pokemmo.db.jooq.tables.records.CharacterRecord;
import org.pokemmo.db.jooq.tables.records.OwnedItemRecord;
import org.pokemmo.db.jooq.tables.records.PokemonRecord;
import org.pokemmo.gameserver.game.item.ItemData;
import org.pokemmo.gameserver.game.item.ItemManager;
import org.pokemmo.gameserver.game.container.PokemonContainerType;
import org.pokemmo.gameserver.util.SnowflakeIdGenerator;
import org.pokemmo.gameserver.services.mail.MailService.ItemAttachment;
import org.pokemmo.gameserver.services.mail.MailService.MailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.ItemMailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.PokemonMailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.MoneyMailAttachment;
import org.pokemmo.gameserver.services.mail.MailService.SendMailResult;
import org.pokemmo.gameserver.services.mail.MailService.MailClaimResult;
import org.pokemmo.gameserver.services.mail.MailService.MailCounts;
import org.pokemmo.gameserver.services.mail.MailService.MailListEntry;
import org.pokemmo.gameserver.services.mail.MailService.MailListPage;
import org.pokemmo.gameserver.services.mail.MailService.MailDetail;
import org.pokemmo.gameserver.services.mail.MailService.MailDeleteResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.pokemmo.db.jooq.Tables.CHARACTER;
import static org.pokemmo.db.jooq.Tables.OWNED_ITEM;
import static org.pokemmo.db.jooq.Tables.POKEMON;

/** Shared mail-table definitions used by focused mail operation services. */
final class MailServiceStore {
    static final int MAIN_INVENTORY_ID = 1;
    static final int MAIL_INVENTORY_ID = 3;
    static final int PC_CONTAINER_ID = 0;
    static final int PARTY_CONTAINER_ID = 1;
    static final int MAIL_CONTAINER_ID = 5;
    static final int PAGE_SIZE = 10;
    /**
     * The client reads the received-mail sender name only when this field is
     * zero. Non-zero values select its tagged/system branch and do not consume
     * the sender-name string.
     */
    static final byte PLAYER_MAIL_TAG_TYPE = 0;
    static final int MAX_RECIPIENT_LENGTH = 32;
    static final int MAX_TITLE_LENGTH = 128;
    static final int MAX_BODY_LENGTH = 4000;

    static final Table<Record> MAIL_MESSAGE = DSL.table(DSL.name("mail_message"));
    static final Field<Long> MAIL_ID =
            DSL.field(DSL.name("mail_message", "mail_id"), Long.class);
    static final Field<Long> MAIL_SENDER_ID =
            DSL.field(DSL.name("mail_message", "sender_id"), Long.class);
    static final Field<Long> MAIL_RECIPIENT_ID =
            DSL.field(DSL.name("mail_message", "recipient_id"), Long.class);
    static final Field<String> MAIL_TITLE =
            DSL.field(DSL.name("mail_message", "title"), String.class);
    static final Field<String> MAIL_BODY =
            DSL.field(DSL.name("mail_message", "body"), String.class);
    static final Field<Boolean> MAIL_IS_READ =
            DSL.field(DSL.name("mail_message", "is_read"), Boolean.class);
    /** PostgreSQL TIMESTAMP is exposed by the project's jOOQ/JDBC setup as Timestamp. */
    static final Field<Timestamp> MAIL_CREATED_AT =
            DSL.field(DSL.name("mail_message", "created_at"), Timestamp.class);

    static final Table<Record> MAIL_ITEM_ATTACHMENT =
            DSL.table(DSL.name("mail_item_attachment"));
    static final Field<Long> MAIL_ITEM_MAIL_ID =
            DSL.field(DSL.name("mail_item_attachment", "mail_id"), Long.class);
    static final Field<Long> MAIL_ITEM_OBJECT_ID =
            DSL.field(DSL.name("mail_item_attachment", "item_object_id"), Long.class);
    static final Field<Short> MAIL_ITEM_AMOUNT =
            DSL.field(DSL.name("mail_item_attachment", "amount"), Short.class);
    static final Field<Boolean> MAIL_ITEM_CLAIMED =
            DSL.field(DSL.name("mail_item_attachment", "claimed"), Boolean.class);

    static final Table<Record> MAIL_POKEMON_ATTACHMENT =
            DSL.table(DSL.name("mail_pokemon_attachment"));
    static final Field<Long> MAIL_POKEMON_MAIL_ID =
            DSL.field(DSL.name("mail_pokemon_attachment", "mail_id"), Long.class);
    static final Field<Long> MAIL_POKEMON_OBJECT_ID =
            DSL.field(DSL.name("mail_pokemon_attachment", "pokemon_object_id"), Long.class);
    static final Field<Boolean> MAIL_POKEMON_CLAIMED =
            DSL.field(DSL.name("mail_pokemon_attachment", "claimed"), Boolean.class);

    static final Table<Record> MAIL_MONEY_ATTACHMENT =
            DSL.table(DSL.name("mail_money_attachment"));
    static final Field<Long> MAIL_MONEY_MAIL_ID =
            DSL.field(DSL.name("mail_money_attachment", "mail_id"), Long.class);
    static final Field<Integer> MAIL_MONEY_AMOUNT =
            DSL.field(DSL.name("mail_money_attachment", "amount"), Integer.class);
    static final Field<Boolean> MAIL_MONEY_CLAIMED =
            DSL.field(DSL.name("mail_money_attachment", "claimed"), Boolean.class);

}
