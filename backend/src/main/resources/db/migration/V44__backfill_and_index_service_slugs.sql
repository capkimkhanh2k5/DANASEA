-- V44__backfill_and_index_service_slugs.sql
-- SEO Optimization: Backfill missing service slugs and create unique index

-- 1. Helper function for Vietnamese slug generation in PostgreSQL
CREATE OR REPLACE FUNCTION generate_vietnamese_slug(input_text TEXT)
RETURNS TEXT AS $$
DECLARE
    normalized TEXT;
BEGIN
    IF input_text IS NULL OR trim(input_text) = '' THEN
        RETURN 'service';
    END IF;

    -- Translate Vietnamese diacritical characters to ASCII equivalents
    normalized := translate(
        lower(input_text),
        'áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđ',
        'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd'
    );

    -- Replace non-alphanumeric characters with hyphens
    normalized := regexp_replace(normalized, '[^a-z0-9]+', '-', 'g');

    -- Trim leading and trailing hyphens
    normalized := regexp_replace(normalized, '^-+|-+$', '', 'g');

    IF normalized = '' THEN
        RETURN 'service';
    END IF;

    RETURN normalized;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 2. Backfill existing services with slug
WITH ranked_services AS (
    SELECT 
        id,
        generate_vietnamese_slug(name) AS base_slug,
        ROW_NUMBER() OVER (
            PARTITION BY generate_vietnamese_slug(name) 
            ORDER BY created_at ASC, id ASC
        ) AS rn
    FROM services
    WHERE slug IS NULL OR trim(slug) = ''
)
UPDATE services s
SET slug = CASE 
    WHEN r.rn = 1 THEN r.base_slug 
    ELSE r.base_slug || '-' || r.rn 
END
FROM ranked_services r
WHERE s.id = r.id;

-- 3. Create unique index on slug for fast lookup and uniqueness enforcement
CREATE UNIQUE INDEX IF NOT EXISTS uk_services_slug ON services(slug) WHERE slug IS NOT NULL;

-- 4. Automatically populate unique slug on INSERT/UPDATE if slug is missing (e.g. demo data seed scripts)
CREATE OR REPLACE FUNCTION trg_fn_services_ensure_slug()
RETURNS TRIGGER AS $$
DECLARE
    base_slug TEXT;
    candidate_slug TEXT;
    suffix INT := 2;
BEGIN
    IF NEW.slug IS NULL OR btrim(NEW.slug) = '' THEN
        base_slug := generate_vietnamese_slug(NEW.name);
        candidate_slug := base_slug;
        WHILE EXISTS (
            SELECT 1 FROM services s
            WHERE s.slug = candidate_slug
              AND (NEW.id IS NULL OR s.id <> NEW.id)
        ) LOOP
            candidate_slug := base_slug || '-' || suffix;
            suffix := suffix + 1;
        END LOOP;
        NEW.slug := candidate_slug;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_services_ensure_slug ON services;
CREATE TRIGGER trg_services_ensure_slug
BEFORE INSERT OR UPDATE ON services
FOR EACH ROW
EXECUTE FUNCTION trg_fn_services_ensure_slug();
