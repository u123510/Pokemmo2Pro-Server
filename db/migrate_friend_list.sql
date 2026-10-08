-- Upgrade an existing database created before friend_list supported multiple friends.
-- Run this once before restarting a server that uses FriendService.
BEGIN;

ALTER TABLE public.friend_list
    DROP CONSTRAINT IF EXISTS friend_list_pkey;

ALTER TABLE public.friend_list
    ADD CONSTRAINT friend_list_pkey PRIMARY KEY (player_id, friend_id);

-- NOT VALID keeps existing orphan rows from blocking the upgrade while still
-- enforcing the foreign key for all new and changed rows.
ALTER TABLE public.friend_list
    ADD CONSTRAINT friend_list_friend_id_fkey
    FOREIGN KEY (friend_id) REFERENCES public.character(id)
    ON DELETE CASCADE NOT VALID;

ALTER TABLE public.friend_list
    ADD CONSTRAINT friend_list_player_not_friend
    CHECK (player_id <> friend_id) NOT VALID;

COMMIT;
