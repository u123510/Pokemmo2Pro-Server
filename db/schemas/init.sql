-- noinspection SqlNoDataSourceInspectionForFile

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ACCOUNTS
CREATE TABLE IF NOT EXISTS "account" (
  account_id SERIAL PRIMARY KEY,
  account_name VARCHAR(12) NOT NULL UNIQUE,
  password bytea NOT NULL,-- SHA-1 hash of the password
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  login_permission SMALLINT NOT NULL DEFAULT 3, -- 登录权限等级，默认值为3
  ban_reason VARCHAR(50) DEFAULT NULL -- 封禁原因
);

-- If accounts is empty, insert a default account admin:admin
INSERT INTO "account" (account_name, password)
  SELECT 'admin', digest('admin', 'sha1')
  WHERE NOT EXISTS (SELECT 1 FROM "account");
-- PERMISSIONS
-- server

--游戏节点表
CREATE TABLE IF NOT EXISTS game_node (
  node_id SERIAL PRIMARY KEY,
  node_type VARCHAR(4) NOT NULL DEFAULT 'game',
  node_name VARCHAR(32) NOT NULL UNIQUE,
  port INT NOT NULL, -- may actually be never used
  permission_id SMALLINT NOT NULL, -- 服务器的权限等级
  is_join_able BOOLEAN NOT NULL DEFAULT FALSE, -- 是否可以加入
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
--添加默认游戏节点
INSERT INTO game_node (node_name, port, permission_id, is_join_able)
VALUES
  ('Schemas', 7777, 2, TRUE),
  ('Pts', 7777, 2, FALSE)
  ON CONFLICT DO NOTHING;
--每个游戏节点对应的真实在线服务器
CREATE TABLE IF NOT EXISTS online_game_node_server(
  server_id SERIAL PRIMARY KEY,
  node_id INT NOT NULL,
  ipv4 INET NOT NULL,
  ipv6 INET NOT NULL,
  port INT NOT NULL,
  FOREIGN KEY (node_id) REFERENCES game_node(node_id) ON DELETE CASCADE,
  UNIQUE (ipv6, ipv4, port)
);
-- 添加默认的在线游戏服务器节点与对话服务器节点
INSERT INTO online_game_node_server (server_id, node_id, ipv4, ipv6, port)
VALUES
  -- 为 Schemas节点添加一个本地游戏服务器
  (1, 1,'127.0.0.1', '::1', 7777)
  ON CONFLICT DO NOTHING;
--每个游戏节点对应的真实在线对话服务器
CREATE TABLE IF NOT EXISTS online_chat_node_server(
  server_id SERIAL PRIMARY KEY,
  node_id INT NOT NULL,
  ipv4 INET NOT NULL,
  ipv6 INET NOT NULL,
  port INT NOT NULL,
  FOREIGN KEY (node_id) REFERENCES game_node(node_id) ON DELETE CASCADE,
  UNIQUE (ipv6, ipv4, port)
);
-- 添加默认的在线对话服务器
INSERT INTO online_chat_node_server (server_id, node_id, ipv4, ipv6, port)
VALUES
  -- 为 Schemas节点添加一个本地聊天服务器
  (1, 1,'127.0.0.1', '::1', 7778)
  ON CONFLICT DO NOTHING;
-- tokens used to authenticate with the server
CREATE TABLE IF NOT EXISTS server_token (
  id SERIAL PRIMARY KEY,
  node_type VARCHAR(4) NOT NULL,
  node_id INT NOT NULL,
  account_id INT NOT NULL,
  account_ip INET NOT NULL,
  token_type VARCHAR(32) NOT NULL,
  token UUID NOT NULL DEFAULT gen_random_uuid(),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (account_id) REFERENCES "account"(account_id) ON DELETE CASCADE,
  UNIQUE (token,node_type,node_id,token_type)
);
--账号的上下文信息
CREATE TABLE IF NOT EXISTS account_context (
  account_id INT NOT NULL PRIMARY KEY,
  character_id BIGINT,
  game_node_id SMALLINT NOT NULL,
  node_name VARCHAR(32) NOT NULL,
  server_id SMALLINT,
  is_log_out BOOLEAN NOT NULL DEFAULT FALSE,
  update_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (account_id) REFERENCES "account"(account_id) ON DELETE CASCADE,
  FOREIGN KEY (game_node_id) REFERENCES game_node(node_id) ON DELETE CASCADE,
  FOREIGN KEY (node_name) REFERENCES game_node(node_name) ON DELETE CASCADE,
  FOREIGN KEY (server_id) REFERENCES online_game_node_server(server_id) ON DELETE CASCADE,
  UNIQUE (account_id)
);
-- used to store pokemon e.g. players party or pc
CREATE TABLE IF NOT EXISTS container (
  id SERIAL PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE,
  size INT NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO container (id, name, size, required)
  VALUES
   (0, 'pc', 660, TRUE),
   (1, 'party', 6, TRUE),
   (2, 'trade', 6, FALSE),
   (3, 'daycare', 26, FALSE),
   (4, 'rental_party', 6, FALSE),
   (5, 'mail', 0, FALSE),
   (6, 'auction', 0, FALSE),
   (7, 'void', 0, FALSE),
   (8, 'deleted', 0, FALSE),
   (9, 'event', 6, FALSE)
  ON CONFLICT DO NOTHING;
-- character
-- maybe we should store the skin data in separate tables?
CREATE TABLE IF NOT EXISTS character (
  -- normal data
    id BIGSERIAL PRIMARY KEY,
    account_id INT NOT NULL,
    name VARCHAR(32) NOT NULL,
    union_name VARCHAR(32) NOT NULL,
    sex SMALLINT NOT NULL DEFAULT 0,
    login_time_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_mac BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    online_minutes INT NOT NULL DEFAULT 0,
    money INT NOT NULL DEFAULT 0,
    coins SMALLINT NOT NULL DEFAULT 0,
    battle_points SMALLINT NOT NULL DEFAULT 0,
    safari_steps SMALLINT NOT NULL DEFAULT 0,
    safari_ball_amount SMALLINT NOT NULL DEFAULT 0,
    other_settings_value SMALLINT NOT NULL DEFAULT 0,
    pc_box_expansion_number SMALLINT NOT NULL DEFAULT 0,
    battle_box_expansion_number SMALLINT NOT NULL DEFAULT 0,
    template_amount SMALLINT NOT NULL DEFAULT 0,
    repel_steps SMALLINT NOT NULL DEFAULT 0,
    repel_item_id SMALLINT NOT NULL DEFAULT 0,
    lure_type SMALLINT NOT NULL DEFAULT 0, -- -1 0 1 2
    lure_item_id SMALLINT NOT NULL DEFAULT 0,
    lure_steps SMALLINT NOT NULL DEFAULT 0,
    -- coordinate data
    region_id SMALLINT NOT NULL DEFAULT 0,
    map_header_id_or_gba_map_group_id SMALLINT NOT NULL DEFAULT 0,
    gba_map_id SMALLINT NOT NULL DEFAULT 0,
    x SMALLINT NOT NULL DEFAULT 0,
    y SMALLINT NOT NULL DEFAULT 0,
    z SMALLINT NOT NULL DEFAULT 0,
    toward SMALLINT NOT NULL DEFAULT 0,
    transportation SMALLINT NOT NULL DEFAULT 0,
    -- 头顶状态标志
    nameplate_status SMALLINT NOT NULL DEFAULT 0,
    -- 权限
    permission SMALLINT NOT NULL DEFAULT 3,
    -- 模型信息
    model_region_index_id SMALLINT NOT NULL DEFAULT -1,
    model_index_id SMALLINT NOT NULL DEFAULT -1,
    -- 跟随宝可梦数据
    follower_pokemon_index_id SMALLINT NOT NULL DEFAULT 0,
    follower_pokemon_rarity SMALLINT NOT NULL DEFAULT 0,
    -- 人物外观数据
    forehead SMALLINT NOT NULL DEFAULT -1,
    forehead_color SMALLINT NOT NULL DEFAULT -1,
    hat SMALLINT NOT NULL DEFAULT -1,
    hat_color SMALLINT NOT NULL DEFAULT -1,
    hair SMALLINT NOT NULL DEFAULT -1,
    hair_color SMALLINT NOT NULL DEFAULT -1,
    eyes SMALLINT NOT NULL DEFAULT -1,
    eyes_color SMALLINT NOT NULL DEFAULT -1,
    facial_hair SMALLINT NOT NULL DEFAULT -1,
    facial_hair_color SMALLINT NOT NULL DEFAULT -1,
    back SMALLINT NOT NULL DEFAULT -1,
    back_color SMALLINT NOT NULL DEFAULT -1,
    top SMALLINT NOT NULL DEFAULT -1,
    top_color SMALLINT NOT NULL DEFAULT -1,
    gloves SMALLINT NOT NULL DEFAULT -1,
    gloves_color SMALLINT NOT NULL DEFAULT -1,
    footwear SMALLINT NOT NULL DEFAULT -1,
    footwear_color SMALLINT NOT NULL DEFAULT -1,
    leggings SMALLINT NOT NULL DEFAULT -1,
    leggings_color SMALLINT NOT NULL DEFAULT -1,
    fishing_rod SMALLINT NOT NULL DEFAULT -1,
    bike SMALLINT NOT NULL DEFAULT 0,
    --event flag data
    champion_flag BOOLEAN[] NOT NULL DEFAULT '{false,false,false,false,false}'::BOOLEAN[],-- 五地区的冠军状态
    running_shoe_flag BOOLEAN[] NOT NULL DEFAULT '{false,false,false,false,false}'::BOOLEAN[], -- 五地区跑步鞋状态
    badge_flag SMALLINT[] NOT NULL DEFAULT '{0,0,0,0,0}'::SMALLINT[], -- 五地区的徽章状态
    story_line_flag SMALLINT[] NOT NULL DEFAULT '{0,0,0,0,0}'::SMALLINT[], -- 五地区的故事线标志
    city_can_fly_flag BIGINT[] NOT NULL DEFAULT '{0,0,0,0,0}'::BIGINT[], -- 五地区的城市可飞状态
    instance_times SMALLINT[] NOT NULL DEFAULT '{0,0,0,0,0}'::SMALLINT[], -- 五地区的天次王数
    instance_next_time TIMESTAMP[] DEFAULT ARRAY[now()::timestamp, now()::timestamp, now()::timestamp, now()::timestamp, now()::timestamp], -- 五地区天王副本下次时间
    first_partner_status SMALLINT[] NOT NULL DEFAULT '{3,3,3,3,3}'::SMALLINT[], -- 五地区初始选择宝可梦状态
    --关都地区事件
    oak_lab_status SMALLINT NOT NULL DEFAULT 0, --大木博士研究所状态
    oak_parcel_status SMALLINT NOT NULL DEFAULT 3 --大木博士包裹状态
);
INSERT INTO character (id,
                       account_id,
                       sex,
                       last_login_mac,
                       online_minutes,
                       money,
                       coins,
                       battle_points,
                       safari_steps,
                       safari_ball_amount,
                       other_settings_value,
                       pc_box_expansion_number,
                       battle_box_expansion_number,
                       template_amount,
                       repel_steps,
                       repel_item_id,
                       lure_type,
                       lure_item_id,
                       lure_steps,
                       region_id,
                       map_header_id_or_gba_map_group_id,
                       gba_map_id,
                       x,
                       y,
                       z,
                       toward,
                       transportation,
                       follower_pokemon_index_id,
                       follower_pokemon_rarity,
                       permission,
                       name,
                       union_name,
                       forehead,
                       forehead_color,
                       hat,
                       hat_color,
                       hair,
                       hair_color,
                       eyes,
                       eyes_color,
                       facial_hair,
                       facial_hair_color,
                       back,
                       back_color,
                       top,
                       top_color,
                       gloves,
                       gloves_color,
                       footwear,
                       footwear_color,
                       leggings,
                       leggings_color)
  SELECT  1, -- character_id
          "account".account_id, -- account_id
          0, --sex
          0, --last_login_mac
          0, --online_minutes
          1500000, --money
          0, --coins
          3500, --battle_points
          0, -- safari_steps
          0, --safari_ball_amount
          0, -- toggle_button_select_bit_map
          2, -- pc_box_expansion_number
          1, -- battle_boxexpansion_number
          0, -- template_amount
          0, -- repel_steps
          0, -- repel_id
          0, -- rure_type
          0, -- rure_item_id
          0, -- rure_steps
          0, -- region_id
          4, -- map_header_id_or_gba_map_group_id
          1, -- gba_map_id
          6, -- x
          6, --y
          0, --z
          0, -- toward
          2, -- transportation
          0, -- fllower_pokemon_id
          0, -- follower_pokemon_pixmapid
          10, -- permission
          'Kyu', -- name
          'ADM', -- union_name
          0,-- forehead
          0,-- forehead_color
          0,-- hat
          0,-- hat_color
          0, -- hair
          0, -- hair_color
          0, -- eyes
          0, -- eyes_color
          0, -- facial_hair
          0, -- facial_hair_color
          0, -- back
          0, -- back_color
          0, -- top
          0, -- top_color
          0, -- gloves
          0, -- gloves_color
          0, -- footwear
          0, -- footwear_color
          0, -- leggings
          0 -- leggings_color
  FROM "account"
  WHERE "account".account_name = 'admin'
  AND NOT EXISTS (SELECT 1 FROM character WHERE account_id = "account".account_id);

-- Per-character story/map event flags generated from the original Kanto scripts.
CREATE TABLE IF NOT EXISTS character_story_event_flag (
  character_id BIGINT NOT NULL,
  flag_name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (character_id, flag_name),
  FOREIGN KEY (character_id) REFERENCES character(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS character_story_action (
  character_id BIGINT NOT NULL,
  action_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (character_id, action_key),
  FOREIGN KEY (character_id) REFERENCES character(id) ON DELETE CASCADE
);

-- TODO: create table with all moves and reference them here
CREATE TABLE IF NOT EXISTS pokemon (
  id BIGSERIAL PRIMARY KEY,
  trainer_id BIGINT NOT NULL,
  widget_type SMALLINT NOT NULL DEFAULT 0,
  container_id INT NOT NULL,
  container_position SMALLINT NOT NULL,
  dex_id SMALLINT NOT NULL,
  personality_value INT NOT NULL DEFAULT (random() * (2147483647::BIGINT - (-2147483648)::BIGINT) + (-2147483648)::BIGINT)::INT,
  original_trainer_id BIGINT NOT NULL,
  ot_name VARCHAR(32) NOT NULL,
  name VARCHAR(32) DEFAULT '',
  mark_status SMALLINT NOT NULL DEFAULT 0,
  status SMALLINT NOT NULL DEFAULT 0,
  level_value SMALLINT NOT NULL,
  current_hp SMALLINT NOT NULL,
  item SMALLINT NOT NULL DEFAULT -1,
  exp INT NOT NULL,
  pp_up_times SMALLINT NOT NULL DEFAULT 0,
  friend_value SMALLINT NOT NULL DEFAULT 0,
  moves SMALLINT[4] NOT NULL,
  moves_pp SMALLINT[4] NOT NULL,
  can_remember_moves SMALLINT[4] NOT NULL,
  ev_values SMALLINT[6] NOT NULL,
  contests_category_values SMALLINT[5] NOT NULL DEFAULT array_fill(0, ARRAY[5]),
  catch_address SMALLINT NOT NULL,
  catch_level SMALLINT NOT NULL,
  catch_region SMALLINT NOT NULL,
  ball_type SMALLINT NOT NULL DEFAULT 3,
  form_type SMALLINT NOT NULL DEFAULT 0,
  iv_values SMALLINT[6] NOT NULL,
  ability SMALLINT NOT NULL DEFAULT 0,
  contest_ribbon SMALLINT[6] NOT NULL DEFAULT array_fill(0, ARRAY[6]),
  normal_ribbon BOOLEAN[16] NOT NULL DEFAULT array_fill(false, ARRAY[16]),
  has_hidden_ability BOOLEAN NOT NULL DEFAULT FALSE,
  is_shiny BOOLEAN NOT NULL DEFAULT FALSE,
  is_alpha BOOLEAN NOT NULL DEFAULT FALSE,
  is_secret BOOLEAN NOT NULL DEFAULT FALSE,
  catch_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  egg_value SMALLINT NOT NULL,
  hidden_power_type SMALLINT NOT NULL DEFAULT -1,
  current_select_particle_effect_type SMALLINT NOT NULL DEFAULT -1,
  particle_effects SMALLINT[] NOT NULL DEFAULT '{}'::SMALLINT[]
);
--为admin账户创建默认宝可梦
INSERT INTO pokemon (trainer_id, container_id, container_position, dex_id, original_trainer_id, ot_name,
                     level_value, current_hp, exp ,moves, moves_pp, can_remember_moves,
                     ev_values,catch_address,catch_level,catch_region,iv_values,
                     egg_value,particle_effects)
  SELECT character.id, container.id, 0, 150, character.id, 'Kyu',
         1, 13,1250000,ARRAY[1, 2, 3, 4], ARRAY[30, 30, 30, 30], ARRAY[5, 6, 7, 8],
         ARRAY[6, 252, 0, 0, 0, 252],1,1,0, Array[ 31, 31, 31, 31, 31, 31],
         0,ARRAY[]::SMALLINT[]
  FROM container, character
  WHERE container.name = 'party'
  AND character.name = 'Kyu'
  AND NOT EXISTS (SELECT 1 FROM pokemon WHERE container_id = container.id AND original_trainer_id = character.id);

-- Global Trade Link listings. Object ownership remains on the source table;
-- active Pokemon listings are moved to the auction container until claimed,
-- sold, or cancelled.
CREATE TABLE IF NOT EXISTS gtl_listing (
  listing_id BIGSERIAL PRIMARY KEY,
  seller_id BIGINT NOT NULL,
  listing_type SMALLINT NOT NULL,
  object_id BIGINT NOT NULL,
  unit_price INT NOT NULL,
  amount SMALLINT NOT NULL,
  original_container_id INT NOT NULL,
  original_container_position SMALLINT NOT NULL,
  status SMALLINT NOT NULL DEFAULT 0,
  sold_amount SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '14 days'),
  FOREIGN KEY (seller_id) REFERENCES character(id) ON DELETE CASCADE,
  FOREIGN KEY (original_container_id) REFERENCES container(id),
  CHECK (listing_type IN (0, 1)),
  CHECK (unit_price > 0),
  CHECK (amount > 0),
  CHECK (sold_amount >= 0 AND sold_amount <= amount)
);

CREATE UNIQUE INDEX IF NOT EXISTS gtl_listing_active_object_idx
  ON gtl_listing (listing_type, object_id)
  WHERE status = 0;

CREATE INDEX IF NOT EXISTS gtl_listing_active_search_idx
  ON gtl_listing (listing_type, status, created_at DESC);

CREATE INDEX IF NOT EXISTS gtl_listing_seller_idx
  ON gtl_listing (seller_id, status, created_at DESC);

-- Completed GTL purchases. This audit record supplies the client's recent
-- trade history and remains independent from whether the seller claimed funds.
CREATE TABLE IF NOT EXISTS gtl_trade_history (
  history_id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  listing_type SMALLINT NOT NULL,
  item_index_id SMALLINT NOT NULL DEFAULT 0,
  pokemon_dex_id SMALLINT NOT NULL DEFAULT 0,
  amount INT NOT NULL,
  unit_price INT NOT NULL,
  total_price INT NOT NULL,
  traded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (listing_id) REFERENCES gtl_listing(listing_id),
  FOREIGN KEY (buyer_id) REFERENCES character(id) ON DELETE CASCADE,
  FOREIGN KEY (seller_id) REFERENCES character(id) ON DELETE CASCADE,
  CHECK (listing_type IN (0, 1)),
  CHECK (amount > 0),
  CHECK (unit_price > 0),
  CHECK (total_price > 0),
  CHECK (
    (listing_type = 0 AND pokemon_dex_id > 0)
    OR (listing_type = 1 AND item_index_id > 0 AND pokemon_dex_id = 0)
  )
);

CREATE INDEX IF NOT EXISTS gtl_trade_history_buyer_idx
  ON gtl_trade_history (buyer_id, traded_at DESC, history_id DESC);

-- ITEMS & INVENTORY
CREATE TABLE IF NOT EXISTS inventory (
  id SMALLINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE
);

INSERT INTO inventory (id, name)
 VALUES
    (0, 'void'),
    (1, 'inventory'),
    (2, 'warehouse'),
    (3, 'mail'),
    (4, 'temporary_event_inventory'),
    (5, 'temporary_shared_event_inventory')
  ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS owned_item (
  item_id BIGINT PRIMARY KEY NOT NULL,
  owner_id BIGINT NOT NULL,
  item_index_id SMALLINT NOT NULL,
  item_amount SMALLINT NOT NULL,
  inventory_id SMALLINT NOT NULL,
  color_id SMALLINT DEFAULT -1,
  item_region_index_id SMALLINT DEFAULT -1,
  pvp_reward_level SMALLINT DEFAULT -1,
  pvp_reward_season SMALLINT DEFAULT -1,
  pvp_reward_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (inventory_id) REFERENCES "inventory"(id) ON DELETE CASCADE
);
INSERT INTO owned_item (item_id,owner_id,item_index_id,item_amount,inventory_id)
 VALUES
   (1,1,5432,1,1),
   (2,1,361,1,1)
   ON CONFLICT DO NOTHING;
INSERT INTO owned_item (item_id,owner_id,item_index_id,item_amount,inventory_id,item_region_index_id)
     VALUES
       (3,1,360,1,1,0)
       ON CONFLICT DO NOTHING;

-- MAIL
-- Mail attachments own their transferred assets in the recipient's mail
-- container until the recipient claims them.
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

--玩家黑名单表
CREATE TABLE IF NOT EXISTS  black_list (
   player_id BIGINT PRIMARY KEY NOT NULL,
   block_player_id BIGINT NOT NULL,
   block_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   block_player_name VARCHAR(32),
   block_reason VARCHAR(10),
   FOREIGN KEY (player_id) REFERENCES character(id) ON DELETE CASCADE
);
--玩家好友表
CREATE TABLE IF NOT EXISTS friend_list (
   player_id BIGINT NOT NULL,
   friend_id BIGINT NOT NULL,
   add_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   PRIMARY KEY (player_id, friend_id),
   FOREIGN KEY (player_id) REFERENCES character(id) ON DELETE CASCADE,
   FOREIGN KEY (friend_id) REFERENCES character(id) ON DELETE CASCADE,
   CHECK (player_id <> friend_id)
);
--玩家图鉴表
CREATE TABLE IF NOT EXISTS pokemon_dex(
   player_id BIGINT PRIMARY KEY NOT NULL,
   meet_level BYTEA NOT NULL DEFAULT decode(repeat('00', 82), 'hex'),--遇见层级
   already_have_level BYTEA NOT NULL DEFAULT decode(repeat('00', 82), 'hex'),--已拥有层级
   caught_level BYTEA NOT NULL DEFAULT decode(repeat('00', 82), 'hex'),--已捕获层级
   caught_alpha_level BYTEA NOT NULL DEFAULT decode(repeat('00', 82), 'hex'),--已捕获头目层级
   FOREIGN KEY (player_id) REFERENCES character(id) ON DELETE CASCADE
);
 INSERT INTO pokemon_dex(player_id,meet_level,already_have_level,caught_level,caught_alpha_level)
     VALUES
       (1,
       decode(repeat('FF', 82), 'hex'),
       decode(repeat('FF', 82), 'hex'),
       decode(repeat('FF', 82), 'hex'),
       decode(repeat('FF', 82), 'hex'))
       ON CONFLICT DO NOTHING;
--玩家死亡存盘表
CREATE TABLE IF NOT EXISTS death_save(
   player_id BIGINT PRIMARY KEY NOT NULL,

   FOREIGN KEY (player_id) REFERENCES character(id) ON DELETE CASCADE
);
