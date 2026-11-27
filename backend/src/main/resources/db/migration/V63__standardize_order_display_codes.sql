-- Standardize persistent customer-facing display codes on master_orders (#ORD-XXXXXXXX)
-- and sub_orders (#SUB-XXXXXXXX) with collision-safe deterministic backfill.
ALTER TABLE master_orders ADD COLUMN IF NOT EXISTS order_code VARCHAR(24);
ALTER TABLE sub_orders ADD COLUMN IF NOT EXISTS sub_order_code VARCHAR(24);

WITH computed_master AS (
    SELECT id,
           'ORD-' || upper(
               CASE
                   WHEN replace(lower(id::text), '-', '') ~ '^(00010000|00020000|00040000|00050000|00060000|00080000|00090000|b0010000|b0020000|00000000)'
                     OR (SELECT count(DISTINCT ch) FROM regexp_split_to_table(substr(replace(lower(id::text), '-', ''), 1, 8), '') AS ch) <= 2
                   THEN substr(replace(lower(id::text), '-', ''), 1, 4) || right(replace(lower(id::text), '-', ''), 4)
                   ELSE substr(replace(lower(id::text), '-', ''), 1, 8)
               END
           ) AS base_code,
           created_at
    FROM master_orders
),
ranked_master AS (
    SELECT id,
           CASE
               WHEN row_number() OVER (PARTITION BY base_code ORDER BY created_at NULLS LAST, id) = 1
               THEN base_code
               ELSE base_code || '-' || row_number() OVER (PARTITION BY base_code ORDER BY created_at NULLS LAST, id)
           END AS final_code
    FROM computed_master
)
UPDATE master_orders m
SET order_code = r.final_code
FROM ranked_master r
WHERE m.id = r.id
  AND (m.order_code IS NULL OR btrim(m.order_code) = '');

WITH computed_sub AS (
    SELECT id,
           'SUB-' || upper(
               CASE
                   WHEN replace(lower(id::text), '-', '') ~ '^(00010000|00020000|00040000|00050000|00060000|00080000|00090000|b0010000|b0020000|00000000)'
                     OR (SELECT count(DISTINCT ch) FROM regexp_split_to_table(substr(replace(lower(id::text), '-', ''), 1, 8), '') AS ch) <= 2
                   THEN substr(replace(lower(id::text), '-', ''), 1, 4) || right(replace(lower(id::text), '-', ''), 4)
                   ELSE substr(replace(lower(id::text), '-', ''), 1, 8)
               END
           ) AS base_code,
           created_at
    FROM sub_orders
),
ranked_sub AS (
    SELECT id,
           CASE
               WHEN row_number() OVER (PARTITION BY base_code ORDER BY created_at NULLS LAST, id) = 1
               THEN base_code
               ELSE base_code || '-' || row_number() OVER (PARTITION BY base_code ORDER BY created_at NULLS LAST, id)
           END AS final_code
    FROM computed_sub
)
UPDATE sub_orders s
SET sub_order_code = r.final_code
FROM ranked_sub r
WHERE s.id = r.id
  AND (s.sub_order_code IS NULL OR btrim(s.sub_order_code) = '');

CREATE UNIQUE INDEX IF NOT EXISTS uq_master_orders_order_code
    ON master_orders (order_code)
    WHERE order_code IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sub_orders_sub_order_code
    ON sub_orders (sub_order_code)
    WHERE sub_order_code IS NOT NULL;
