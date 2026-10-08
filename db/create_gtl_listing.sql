-- Run this script against the same PostgreSQL database used by server.game.
-- The referenced public.character and public.container tables must already exist.

BEGIN;

CREATE TABLE IF NOT EXISTS public.gtl_listing (
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
  FOREIGN KEY (seller_id) REFERENCES public.character(id) ON DELETE CASCADE,
  FOREIGN KEY (original_container_id) REFERENCES public.container(id),
  CHECK (listing_type IN (0, 1)),
  CHECK (unit_price > 0),
  CHECK (amount > 0),
  CHECK (sold_amount >= 0 AND sold_amount <= amount)
);

CREATE UNIQUE INDEX IF NOT EXISTS gtl_listing_active_object_idx
  ON public.gtl_listing (listing_type, object_id)
  WHERE status = 0;

CREATE INDEX IF NOT EXISTS gtl_listing_active_search_idx
  ON public.gtl_listing (listing_type, status, created_at DESC);

CREATE INDEX IF NOT EXISTS gtl_listing_seller_idx
  ON public.gtl_listing (seller_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS public.gtl_trade_history (
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
  FOREIGN KEY (listing_id) REFERENCES public.gtl_listing(listing_id),
  FOREIGN KEY (buyer_id) REFERENCES public.character(id) ON DELETE CASCADE,
  FOREIGN KEY (seller_id) REFERENCES public.character(id) ON DELETE CASCADE,
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
  ON public.gtl_trade_history (buyer_id, traded_at DESC, history_id DESC);

COMMIT;

SELECT to_regclass('public.gtl_listing') AS listing_table,
       to_regclass('public.gtl_trade_history') AS history_table;
