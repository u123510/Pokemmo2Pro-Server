-- Apply manually to an existing database before enabling Unova death saves.
BEGIN;
ALTER TABLE public.death_save
    ADD COLUMN IF NOT EXISTS unova_point_id SMALLINT NOT NULL DEFAULT 200;
ALTER TABLE public.death_save DROP CONSTRAINT IF EXISTS death_save_unova_point_id_check;
ALTER TABLE public.death_save ALTER COLUMN unova_point_id SET DEFAULT 200;

UPDATE public.death_save
SET unova_point_id = unova_point_id + 179
WHERE unova_point_id BETWEEN 21 AND 34;

ALTER TABLE public.death_save
    ADD CONSTRAINT death_save_unova_point_id_check CHECK (unova_point_id BETWEEN 200 AND 213);
COMMIT;
