-- Mail schema migration for databases created before mail support.
-- Run after the base character, pokemon and owned_item tables exist.

CREATE TABLE IF NOT EXISTS mail_message (
  mail_id BIGSERIAL PRIMARY KEY,
  sender_id BIGINT NOT NULL,
  recipient_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL DEFAULT '',
  body TEXT NOT NULL DEFAULT '',
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (sender_id) REFERENCES character(id) ON DELETE CASCADE,
  FOREIGN KEY (recipient_id) REFERENCES character(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS mail_message_recipient_idx
  ON mail_message (recipient_id, created_at DESC, mail_id DESC);
CREATE INDEX IF NOT EXISTS mail_message_sender_idx
  ON mail_message (sender_id, created_at DESC, mail_id DESC);
CREATE INDEX IF NOT EXISTS mail_message_unread_idx
  ON mail_message (recipient_id, is_read)
  WHERE is_read = FALSE;

CREATE TABLE IF NOT EXISTS mail_item_attachment (
  attachment_id BIGSERIAL PRIMARY KEY,
  mail_id BIGINT NOT NULL,
  item_object_id BIGINT NOT NULL,
  amount SMALLINT NOT NULL,
  claimed BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (mail_id) REFERENCES mail_message(mail_id) ON DELETE CASCADE,
  FOREIGN KEY (item_object_id) REFERENCES owned_item(item_id) ON DELETE CASCADE,
  CHECK (amount > 0),
  UNIQUE (mail_id, item_object_id)
);

CREATE TABLE IF NOT EXISTS mail_pokemon_attachment (
  mail_id BIGINT NOT NULL,
  pokemon_object_id BIGINT NOT NULL,
  claimed BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (mail_id) REFERENCES mail_message(mail_id) ON DELETE CASCADE,
  FOREIGN KEY (pokemon_object_id) REFERENCES pokemon(id) ON DELETE CASCADE,
  PRIMARY KEY (mail_id, pokemon_object_id),
  UNIQUE (pokemon_object_id)
);

CREATE TABLE IF NOT EXISTS mail_money_attachment (
  mail_id BIGINT PRIMARY KEY,
  amount INT NOT NULL,
  claimed BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (mail_id) REFERENCES mail_message(mail_id) ON DELETE CASCADE,
  CHECK (amount > 0)
);

ALTER TABLE mail_item_attachment
  ADD COLUMN IF NOT EXISTS claimed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mail_pokemon_attachment
  ADD COLUMN IF NOT EXISTS claimed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mail_money_attachment
  ADD COLUMN IF NOT EXISTS claimed BOOLEAN NOT NULL DEFAULT FALSE;
