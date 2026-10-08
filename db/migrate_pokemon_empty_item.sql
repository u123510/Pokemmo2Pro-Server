-- Canonicalize legacy empty held-item values.
-- Positive values remain real held-item indexes; 0 is the historical empty value.
UPDATE public.pokemon
SET item = -1
WHERE item <= 0;

ALTER TABLE public.pokemon
  ALTER COLUMN item SET DEFAULT -1;
