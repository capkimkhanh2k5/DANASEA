-- Enforce referential integrity for services.category_id:
-- Prevent deletion of a category when services reference it (ON DELETE RESTRICT).
-- Also prevent cascade deletion of commission rules and safety rules.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_services_category_id'
    ) THEN
        ALTER TABLE services
            ADD CONSTRAINT fk_services_category_id
            FOREIGN KEY (category_id)
            REFERENCES categories (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_categories_parent_id'
    ) THEN
        ALTER TABLE categories
            ADD CONSTRAINT fk_categories_parent_id
            FOREIGN KEY (parent_id)
            REFERENCES categories (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

-- Harden commission_rules and category_safety_rules to prevent cascade deletion
ALTER TABLE commission_rules
    DROP CONSTRAINT IF EXISTS commission_rules_category_id_fkey,
    ADD CONSTRAINT commission_rules_category_id_fkey
    FOREIGN KEY (category_id)
    REFERENCES categories(id)
    ON DELETE RESTRICT;

ALTER TABLE category_safety_rules
    DROP CONSTRAINT IF EXISTS category_safety_rules_category_id_fkey,
    ADD CONSTRAINT category_safety_rules_category_id_fkey
    FOREIGN KEY (category_id)
    REFERENCES categories(id)
    ON DELETE RESTRICT;
