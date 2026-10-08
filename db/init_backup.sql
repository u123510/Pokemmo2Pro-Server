-- noinspection SqlNoDataSourceInspectionForFile

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- USERS
CREATE TABLE IF NOT EXISTS "user" (
  id SERIAL PRIMARY KEY,
  username VARCHAR(12) NOT NULL UNIQUE,
  -- SHA-1 hash of the password
  password bytea NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- If users is empty, insert a default user admin:admin
INSERT INTO "user" (username, password)
  SELECT 'admin', digest('admin', 'sha1')
  WHERE NOT EXISTS (SELECT 1 FROM "user");

-- PERMISSIONS

CREATE TABLE IF NOT EXISTS permissions (
  id SERIAL PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE,
  permission_value SMALLINT NOT NULL UNIQUE
);
INSERT INTO permissions (id,name, permission_value)
  VALUES
   (1,'BANNED', 1),
   (2,'MUTED', 2),
   (3,'NORMAL', 3),
   (4,'STAFF', 4),
   (5,'CM', 5),
   (6,'MOD', 6),
   (7,'GM', 7),
   (8,'SGM', 8),
   (9,'HGM', 9),
   (10,'ADM', 10)
  ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS user_permission (
  user_id INT NOT NULL,
  permission_id INT NOT NULL,
  user_permission_value SMALLINT NOT NULL DEFAULT 3,
  PRIMARY KEY (user_id, permission_id),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
  FOREIGN KEY (user_permission_value) REFERENCES permissions(permission_value) ON DELETE CASCADE
);

-- If permissions is empty, insert a default permission admin and assign it to the default "user"
INSERT INTO permissions (name)
  SELECT 'admin'
  WHERE NOT EXISTS (SELECT 1 FROM permissions);

INSERT INTO user_permission (user_id, permission_id, user_permission_value)
    SELECT "user".id, permissions.id, 10 --ADM permission
    FROM "user", permissions
    WHERE "user".username = 'admin' AND permissions.name = 'admin'
    AND NOT EXISTS (SELECT 1 FROM user_permission WHERE user_id = "user".id AND permission_id = permissions.id);

-- server

-- this tables stores the server that are currently online
-- server are made up of multiple nodes which are all synchronized with each other
CREATE TABLE IF NOT EXISTS server (
  id SERIAL PRIMARY KEY,
  type VARCHAR(4) NOT NULL, -- possible values: "game", "chat"
  name VARCHAR(32) NOT NULL UNIQUE,
  port INT NOT NULL, -- may actually be never used
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- specifies the necessary permissions for each server
CREATE TABLE IF NOT EXISTS server_permission (
  server_id INT NOT NULL,
  permission_id INT NOT NULL,
  PRIMARY KEY (server_id, permission_id),
  FOREIGN KEY (server_id) REFERENCES server(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- this table stores the server that are currently online
-- each server is part of a server group it needs a unique ip and port combination
CREATE TABLE IF NOT EXISTS server_node (
  id SERIAL PRIMARY KEY,
  server_id INT NOT NULL,
  ipv4 INET NOT NULL,
  ipv6 INET NOT NULL,
  port INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (server_id) REFERENCES server(id) ON DELETE CASCADE,
  UNIQUE (ipv6, ipv4, port)
);

-- add a default server for the game server
INSERT INTO server (type, name, port)
  SELECT 'game', 'PokeMMO2', 7777
  WHERE NOT EXISTS (SELECT 1 FROM server WHERE type = 'game' AND name = 'PokeMMO2');

-- add a default server node for the OpenMMO server
INSERT INTO server_node (server_id, ipv4, ipv6, port)
  SELECT server.id, '127.0.0.1', '::1', 7777
  FROM server
  WHERE server.type = 'game' AND server.name = 'PokeMMO2'
  AND NOT EXISTS (SELECT 1 FROM server_node WHERE server_id = server.id);

-- SERVER SESSIONS

-- tokens used to authenticate with the server
CREATE TABLE IF NOT EXISTS server_token (
  id SERIAL PRIMARY KEY,
  server_id INT NOT NULL,
  user_id INT NOT NULL,
  user_ip INET NOT NULL,
  token UUID NOT NULL DEFAULT gen_random_uuid(),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (server_id) REFERENCES server(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
  UNIQUE (token, server_id)
);
--event_flag
CREATE TABLE IF NOT EXISTS event_flag (
  event_type SMALLINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE
);
INSERT INTO event_flag (event_type,name)
  VALUES
   (0,'kanto'),
   (1,'hoenn'),
   (2,'unova'),
   (3,'sinnoh'),
   (4,'johto'),
   (10,'unk'),
   (128,'unk1')
   ON CONFLICT DO NOTHING;
--game_event_flags
CREATE TABLE IF NOT EXISTS game_event_flags (
  event_type SMALLINT,
  event_value SMALLINT,
  event_status SMALLINT,
  name VARCHAR(64) NOT NULL UNIQUE,
  FOREIGN KEY (event_type) REFERENCES event_flag(event_type) ON DELETE CASCADE
);
INSERT INTO game_event_flags (event_type,event_value,event_status,name)
  VALUES
   (0,2080,0,'Received_Boulder_Badge'),
   (0,2081,0,'Received_Cascade_Badge'),
   (0,2082,0,'Received_Thunder_Badge'),
   (0,2083,0,'Received_Rainbow_Badge'),
   (0,2084,0,'Received_Soul_Badge'),
   (0,2085,0,'Received_Marsh_Badge'),
   (0,2086,0,'Received_Volcano_Badge'),
   (0,2087,0,'Received_Earth_Badge'),
   (0,2092,0,'Dont_Spawn_Prof_Oak_In_Pallet_Town'),
   (0,2095,0,''),
   (0,2192,0,''),
   (0,2193,0,''),
   (0,2194,0,''),
   (0,2195,0,''),
   (0,2196,0,''),
   (0,2197,0,''),
   (0,2198,0,''),
   (0,2199,0,''),
   (0,2200,0,''),
   (0,2201,0,''),
   (0,2202,0,'')
  ON CONFLICT DO NOTHING;
--game_event_server_flags
CREATE TABLE IF NOT EXISTS game_event_server_flags (
   event_type SMALLINT,
   event_status SMALLINT,
   name VARCHAR(64) NOT NULL UNIQUE,
   FOREIGN KEY (event_type) REFERENCES event_flag(event_type) ON DELETE CASCADE
);
INSERT INTO game_event_server_flags (event_type,event_status,name)
  VALUES
   (0,0,'Dont_Spawn_Bullbasaur_Ball_In_Oak_Lab'),
   (0,0,'Dont_Spawn_Squirtle_Ball_In_Oak_Lab'),
   (0,0,'Dont_Spawn_Charmander_Ball_In_Oak_Lab')
  ON CONFLICT DO NOTHING;
--event_constants
CREATE TABLE IF NOT EXISTS event_constants (
  name VARCHAR(32) NOT NULL UNIQUE,
  event_status SMALLINT
);
INSERT INTO event_constants (name,event_status)
  VALUES
   ('Oak_Lab_Status',10),
   ('Oak_Parcel',3),
   ('Starter',3)
  ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS instance_info(
    instance_type SMALLINT,
    next_instance_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finish_times SMALLINT
);
INSERT INTO instance_info (instance_type,finish_times)
  VALUES
   (4,5),
   (3,7),
   (2,10),
   (1,15),
   (0,22)
  ON CONFLICT DO NOTHING;

-- POKEMON & container

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
   (4, 'rentalParty', 6, FALSE),
   (5, 'mail', 0, FALSE),
   (6, 'auction', 0, FALSE),
   (7, 'void', 0, FALSE),
   (8, 'deleted', 0, FALSE),
   (9, 'event', 6, FALSE)
  ON CONFLICT DO NOTHING;
-- map & region
CREATE TABLE IF NOT EXISTS region (
  id SMALLINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE
);
INSERT INTO region (id, name)
 VALUES
    (0, 'kanto'),
    (1, 'hoenn'),
    (2, 'unova'),
    (3, 'sinnoh'),
    (4, 'johto')
  ON CONFLICT DO NOTHING;
-- character
-- maybe we should store the skin data in separate tables?
CREATE TABLE IF NOT EXISTS character (
  -- normal data
    id BIGSERIAL PRIMARY KEY,
    user_id INT NOT NULL,
    sex SMALLINT NOT NULL DEFAULT 0,
    rival_sex SMALLINT NOT NULL DEFAULT 0,
    login_time_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_mac BIGINT NOT NULL DEFAULT 0,
    begin_time_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    online_minutes INT NOT NULL DEFAULT 0,
    money INT NOT NULL DEFAULT 0,
    coins SMALLINT NOT NULL DEFAULT 0,
    battle_points SMALLINT NOT NULL DEFAULT 0,
    safari_steps SMALLINT NOT NULL DEFAULT 0,
    safari_ball_amount SMALLINT NOT NULL DEFAULT 0,
    toggle_button_select_bit_map SMALLINT NOT NULL DEFAULT 0,
    pc_box_expansion_number SMALLINT NOT NULL DEFAULT 0,
    battle_boxexpansion_number SMALLINT NOT NULL DEFAULT 0,
    template_amount SMALLINT NOT NULL DEFAULT 0,
    repel_steps SMALLINT NOT NULL DEFAULT 0,
    repel_item_id SMALLINT NOT NULL DEFAULT 0,
    class_type SMALLINT NOT NULL DEFAULT -1, -- -1 0 1 2
    rure_item_id SMALLINT NOT NULL DEFAULT 0,
    rure_steps SMALLINT NOT NULL DEFAULT 0,
    -- coordinate data
    region_id SMALLINT NOT NULL DEFAULT 0,
    bank_id SMALLINT NOT NULL DEFAULT 0,
    map_id SMALLINT NOT NULL DEFAULT 0,
    direction SMALLINT NOT NULL DEFAULT 0,
    x SMALLINT NOT NULL DEFAULT 0,
    y SMALLINT NOT NULL DEFAULT 0,
    toward SMALLINT NOT NULL DEFAULT 0,
    transportation SMALLINT NOT NULL DEFAULT 0,
    flag SMALLINT NOT NULL DEFAULT 0,
    fllower_pokemon_id SMALLINT NOT NULL DEFAULT 0,
    follower_pokemon_pixmapid SMALLINT NOT NULL DEFAULT 0,
    -- permission
    permission_value SMALLINT NOT NULL DEFAULT 3,
    -- Battle data
    battle_status SMALLINT NOT NULL DEFAULT 0,
    name VARCHAR(32) NOT NULL,
    union_name VARCHAR(32) NOT NULL,
    -- skin data
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    FOREIGN KEY (region_id) REFERENCES region(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_value) REFERENCES permissions(permission_value) ON DELETE CASCADE
);
INSERT INTO character (id, user_id, sex, rival_sex, last_login_mac,online_minutes, money, coins,
                       battle_points, safari_steps, safari_ball_amount,
                       toggle_button_select_bit_map, pc_box_expansion_number, battle_boxexpansion_number,
                       template_amount, repel_steps, repel_item_id, class_type, rure_item_id, rure_steps,
                       region_id,bank_id,map_id,direction,x, y, toward, transportation, fllower_pokemon_id, follower_pokemon_pixmapid, permission_value, battle_status,
                       name, union_name, forehead_color, hat, hat_color, hair, hair_color, eyes, eyes_color,
                       facial_hair, facial_hair_color, back, back_color, top, top_color, gloves, gloves_color,
                       footwear, footwear_color, leggings, leggings_color)
  SELECT  1,"user".id, 0, 0, 0, 0, 0, 0,
          0, 0, 0,
          0, 0, 0,
          0, 0, 0, -1, 0, 0,
          0, 4, 1, 2, 6 ,6, 0, 0, 150, 32, 10, 0,
          'Kyu','ADM',
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
  FROM "user"
  WHERE "user".username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM character WHERE user_id = "user".id);
-- TODO: create table with all moves and reference them here
CREATE TABLE IF NOT EXISTS pokemon (
  id BIGSERIAL PRIMARY KEY,
  trainer_id BIGINT NOT NULL,
  widget_type SMALLINT NOT NULL DEFAULT 2,
  container_id INT NOT NULL,
  container_position SMALLINT NOT NULL,
  dex_id SMALLINT NOT NULL,
  personality_value INT NOT NULL DEFAULT (random() * (2147483647::BIGINT - (-2147483648)::BIGINT) + (-2147483648)::BIGINT)::INT,
  original_trainer_id BIGINT NOT NULL,
  ot_name VARCHAR(32) NOT NULL,
  name VARCHAR(32) DEFAULT '',
  color_type SMALLINT NOT NULL DEFAULT 0,
  status SMALLINT NOT NULL DEFAULT 0,
  level_value SMALLINT NOT NULL,
  current_hp SMALLINT NOT NULL,
  max_hp SMALLINT NOT NULL,
    item SMALLINT NOT NULL DEFAULT -1,
  exp INT NOT NULL,
  pp_up_times SMALLINT NOT NULL DEFAULT 0,
  friend_value SMALLINT NOT NULL DEFAULT 0,
  moves SMALLINT[4] NOT NULL,
  moves_pp SMALLINT[4] NOT NULL,
  can_remember_moves SMALLINT[4] NOT NULL,
  ev_values SMALLINT[6] NOT NULL,
  cool_value SMALLINT NOT NULL DEFAULT 0,
  beauti_value SMALLINT NOT NULL DEFAULT 0,
  cute_value SMALLINT NOT NULL DEFAULT 0,
  clever_value SMALLINT NOT NULL DEFAULT 0,
  strong_value SMALLINT NOT NULL DEFAULT 0,
  catch_addr SMALLINT NOT NULL,
  catch_level SMALLINT NOT NULL,
  catch_region SMALLINT NOT NULL,
  ball_type SMALLINT NOT NULL DEFAULT 3,
  spec_type SMALLINT NOT NULL DEFAULT 0,
  iv_values SMALLINT[6] NOT NULL,
  ability SMALLINT NOT NULL DEFAULT 0,
  ribbon BOOLEAN[34] NOT NULL DEFAULT array_fill(false, ARRAY[34]),
  has_hidden_ability BOOLEAN NOT NULL DEFAULT FALSE,
  is_shiny BOOLEAN NOT NULL DEFAULT FALSE,
  is_alpha BOOLEAN NOT NULL DEFAULT FALSE,
  is_secret BOOLEAN NOT NULL DEFAULT FALSE,
  catch_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  egg_value SMALLINT NOT NULL,
  pokemon_type SMALLINT NOT NULL DEFAULT -1,
  particle_effect_type SMALLINT NOT NULL DEFAULT -1,
  particle_effects SMALLINT[] NOT NULL DEFAULT '{}'::SMALLINT[],
  FOREIGN KEY (catch_region) REFERENCES region(id) ON DELETE CASCADE,
  FOREIGN KEY (trainer_id) REFERENCES character(id) ON DELETE CASCADE,
  FOREIGN KEY (original_trainer_id) REFERENCES character(id) ON DELETE CASCADE,
  FOREIGN KEY (container_id) REFERENCES container(id) ON DELETE CASCADE
);
-- create a default pokemon for the default "user"
INSERT INTO pokemon (trainer_id, container_id, container_position, dex_id, original_trainer_id, ot_name,
                     level_value, current_hp, max_hp, exp ,moves, moves_pp, can_remember_moves,
                     ev_values,catch_addr,catch_level,catch_region,iv_values,
                     egg_value,particle_effects)
  SELECT character.id, container.id, 0, 150, character.id, 'Kyu',
         100, 416,416,0,ARRAY[1, 2, 3, 4], ARRAY[30, 30, 30, 30], ARRAY[5, 6, 7, 8],
         ARRAY[252, 252, 252, 252, 252, 252],1,100,0, Array[ 31, 31, 31, 31, 31, 31],
         0,ARRAY[]::SMALLINT[]
  FROM container, character
  WHERE container.name = 'party'
  AND character.name = 'Kyu'
  AND NOT EXISTS (SELECT 1 FROM pokemon WHERE container_id = container.id AND original_trainer_id = character.id);

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
CREATE TABLE IF NOT EXISTS item (
  id BIGINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE
);
INSERT INTO item (id, name)
 VALUES
    (1, '')
  ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS owned_item (
  item_flag SMALLINT NOT NULL,
  item_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  item_index SMALLINT NOT NULL,
  item_amount SMALLINT NOT NULL,
  inventory_id SMALLINT NOT NULL,
  color_id SMALLINT,
  item_type SMALLINT,
  pvp_reward_level SMALLINT DEFAULT -1,
  pvp_reward_season SMALLINT DEFAULT -1,
  pvp_reward_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE CASCADE,
  FOREIGN KEY (owner_id) REFERENCES character(id) ON DELETE CASCADE,
  FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE,
  CONSTRAINT item_owned_pk PRIMARY KEY (item_id, owner_id, inventory_id)
);
INSERT INTO owned_item (item_flag,item_id,owner_id,item_index,item_amount,inventory_id,color_id,item_type,pvp_reward_level,pvp_reward_season)
 VALUES
   (8,1,1,5432,1,1,0,2,1,21)
   ON CONFLICT DO NOTHING;
 CREATE TABLE IF NOT EXISTS owned_pokemon_effect(
   effect_id SMALLINT NOT NULL
  );
INSERT INTO owned_pokemon_effect (effect_id)
 VALUES
   (5),
   (6),
   (7),
   (8)
   ON CONFLICT DO NOTHING;
-- default character for the default "user"


