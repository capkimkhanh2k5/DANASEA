-- V40__unify_platform_commission_policy.sql
-- Decouple Flash Sale from vendor-specific commission policies to enforce
-- PLATFORM_COMMISSION_RATE (Admin System Configuration) as the single source of truth.

-- 1. Remove foreign key constraints to vendor_commission_policies
ALTER TABLE flash_sales
    DROP CONSTRAINT IF EXISTS flash_sales_policy_id_fkey;

ALTER TABLE flash_sale_allocations
    DROP CONSTRAINT IF EXISTS flash_sale_allocations_policy_id_fkey;

-- 2. Make policy_id nullable on flash_sale_allocations so allocations can be created without vendor policy
ALTER TABLE flash_sale_allocations
    ALTER COLUMN policy_id DROP NOT NULL;

ALTER TABLE flash_sale_allocations
    ALTER COLUMN policy_version SET DEFAULT 1;

-- 3. Relax check constraint on flash_sales: policy_id is no longer mandatory when enabled
ALTER TABLE flash_sales
    DROP CONSTRAINT IF EXISTS flash_sales_check1;

ALTER TABLE flash_sales
    ADD CONSTRAINT flash_sales_check1 CHECK (
        status <> 'ENABLED' OR (original_unit_price IS NOT NULL AND original_unit_price > unit_subsidy)
    );

-- 4. Replace allocation guard trigger to validate against campaign terms without mandatory vendor policy lookup
CREATE OR REPLACE FUNCTION guard_flash_sale_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE campaign flash_sales%ROWTYPE; used_quantity bigint; used_budget numeric;
BEGIN
    IF TG_OP='UPDATE' THEN
        IF (to_jsonb(NEW)-'state'-'updated_at') IS DISTINCT FROM (to_jsonb(OLD)-'state'-'updated_at')
            OR OLD.state <> 'RESERVED' OR NEW.state NOT IN ('CONSUMED','RELEASED') THEN
            RAISE EXCEPTION 'Allocation snapshots are immutable; terminal transitions occur once';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT campaign FROM flash_sales WHERE id=NEW.flash_sale_id FOR UPDATE;
    IF NEW.original_unit_price IS DISTINCT FROM campaign.original_unit_price
        OR NEW.unit_subsidy <> campaign.unit_subsidy THEN
        RAISE EXCEPTION 'Allocation does not match the enabled financial terms';
    END IF;
    IF NEW.state <> 'RESERVED' OR campaign.status <> 'ENABLED'
        OR clock_timestamp() < campaign.starts_at OR clock_timestamp() >= campaign.ends_at THEN
        RAISE EXCEPTION 'Flash Sale unavailable';
    END IF;
    SELECT coalesce(sum(quantity),0),coalesce(sum(subsidy_amount),0) INTO used_quantity,used_budget
        FROM flash_sale_allocations WHERE flash_sale_id=NEW.flash_sale_id AND state IN ('RESERVED','CONSUMED');
    IF used_quantity+NEW.quantity>campaign.quota OR used_budget+NEW.subsidy_amount>campaign.budget THEN
        RAISE EXCEPTION 'Flash Sale quota or budget exhausted';
    END IF;
    RETURN NEW;
END $$;
