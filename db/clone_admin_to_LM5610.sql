-- 将 admin 账号复制为 LM5610。
-- 默认登录密码：LM5610
--
-- 会复制：账号权限、角色、宝可梦、道具、图鉴、死亡存档。
-- 不复制：server_token、account_context、黑名单和好友关系。
-- 请在 mmo_db 数据库中执行本脚本。

BEGIN;

DO $$
DECLARE
    source_account_name TEXT := 'admin';
    target_account_name TEXT := 'LM5610';
    target_password TEXT := 'LM5610';

    source_account_id INTEGER;
    target_account_id INTEGER;
    source_character RECORD;
    source_pokemon RECORD;
    source_item RECORD;
    source_dex RECORD;
    source_death_save RECORD;
    character_map RECORD;
    target_character_id BIGINT;
    next_item_id BIGINT;
BEGIN
    IF length(target_account_name) > 12 THEN
        RAISE EXCEPTION '账号名超过 12 个字符: %', target_account_name;
    END IF;

    SELECT account_id
    INTO source_account_id
    FROM public."account"
    WHERE account_name = source_account_name;

    IF source_account_id IS NULL THEN
        RAISE EXCEPTION '源账号不存在: %', source_account_name;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public."account"
        WHERE account_name = target_account_name
    ) THEN
        RAISE EXCEPTION '目标账号已存在: %', target_account_name;
    END IF;

    -- 初始化脚本显式插入过部分自增 ID，先同步序列，避免复制时主键冲突。
    IF EXISTS (SELECT 1 FROM public."account") THEN
        PERFORM setval(
            pg_get_serial_sequence('public.account', 'account_id'),
            (SELECT max(account_id) FROM public."account"),
            TRUE
        );
    END IF;

    IF EXISTS (SELECT 1 FROM public.character) THEN
        PERFORM setval(
            pg_get_serial_sequence('public.character', 'id'),
            (SELECT max(id) FROM public.character),
            TRUE
        );
    END IF;

    IF EXISTS (SELECT 1 FROM public.pokemon) THEN
        PERFORM setval(
            pg_get_serial_sequence('public.pokemon', 'id'),
            (SELECT max(id) FROM public.pokemon),
            TRUE
        );
    END IF;

    -- 复制账号权限，但不复制封禁原因。
    INSERT INTO public."account"
        (account_name, password, login_permission, ban_reason)
    SELECT
        target_account_name,
        digest(target_password, 'sha1'),
        login_permission,
        NULL
    FROM public."account"
    WHERE account_id = source_account_id
    RETURNING account_id INTO target_account_id;

    CREATE TEMP TABLE _clone_character_map (
        old_id BIGINT PRIMARY KEY,
        new_id BIGINT NOT NULL
    ) ON COMMIT DROP;

    -- 复制源账号的全部角色，并建立旧角色 ID 到新角色 ID 的映射。
    FOR source_character IN
        SELECT c.*
        FROM public.character AS c
        WHERE c.account_id = source_account_id
        ORDER BY c.id
    LOOP
        INSERT INTO public.character
        SELECT (
            jsonb_populate_record(
                NULL::public.character,
                to_jsonb(source_character) || jsonb_build_object(
                    'id', nextval(pg_get_serial_sequence('public.character', 'id')),
                    'account_id', target_account_id
                )
            )
        ).* 
        RETURNING id INTO target_character_id;

        INSERT INTO _clone_character_map (old_id, new_id)
        VALUES (source_character.id, target_character_id);
    END LOOP;

    -- 复制角色下的宝可梦。
    FOR character_map IN
        SELECT old_id, new_id
        FROM _clone_character_map
        ORDER BY old_id
    LOOP
        FOR source_pokemon IN
            SELECT p.*
            FROM public.pokemon AS p
            WHERE p.trainer_id = character_map.old_id
            ORDER BY p.id
        LOOP
            INSERT INTO public.pokemon
            SELECT (
                jsonb_populate_record(
                    NULL::public.pokemon,
                    to_jsonb(source_pokemon) || jsonb_build_object(
                        'id', nextval(pg_get_serial_sequence('public.pokemon', 'id')),
                        'trainer_id', character_map.new_id,
                        'original_trainer_id', COALESCE(
                            (
                                SELECT m.new_id
                                FROM _clone_character_map AS m
                                WHERE m.old_id = source_pokemon.original_trainer_id
                            ),
                            source_pokemon.original_trainer_id
                        )
                    )
                )
            ).*;
        END LOOP;

        -- owned_item 没有自增序列，因此为新角色分配新的 item_id。
        SELECT COALESCE(max(item_id), 0)
        INTO next_item_id
        FROM public.owned_item;

        FOR source_item IN
            SELECT oi.*
            FROM public.owned_item AS oi
            WHERE oi.owner_id = character_map.old_id
            ORDER BY oi.item_id
        LOOP
            next_item_id := next_item_id + 1;

            INSERT INTO public.owned_item
            SELECT (
                jsonb_populate_record(
                    NULL::public.owned_item,
                    to_jsonb(source_item) || jsonb_build_object(
                        'item_id', next_item_id,
                        'owner_id', character_map.new_id
                    )
                )
            ).*;
        END LOOP;

        -- 复制图鉴。
        FOR source_dex IN
            SELECT d.*
            FROM public.pokemon_dex AS d
            WHERE d.player_id = character_map.old_id
        LOOP
            INSERT INTO public.pokemon_dex
            SELECT (
                jsonb_populate_record(
                    NULL::public.pokemon_dex,
                    to_jsonb(source_dex) || jsonb_build_object(
                        'player_id', character_map.new_id
                    )
                )
            ).*;
        END LOOP;

        -- 复制死亡存档（当前表只有 player_id）。
        FOR source_death_save IN
            SELECT ds.*
            FROM public.death_save AS ds
            WHERE ds.player_id = character_map.old_id
        LOOP
            INSERT INTO public.death_save
            SELECT (
                jsonb_populate_record(
                    NULL::public.death_save,
                    to_jsonb(source_death_save) || jsonb_build_object(
                        'player_id', character_map.new_id
                    )
                )
            ).*;
        END LOOP;
    END LOOP;

    RAISE NOTICE '已创建账号 %, account_id=%', target_account_name, target_account_id;
END $$;

COMMIT;

-- 验证新账号及其角色：
-- SELECT a.account_id, a.account_name, c.id AS character_id, c.name AS character_name
-- FROM public."account" a
-- LEFT JOIN public.character c ON c.account_id = a.account_id
-- WHERE a.account_name = 'LM5610';
