CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE vendor_commission_policies (
    id uuid PRIMARY KEY,
    vendor_id uuid NOT NULL REFERENCES vendors(id) ON DELETE RESTRICT,
    version integer NOT NULL CHECK (version > 0),
    commission_rate numeric(9,6) NOT NULL CHECK (commission_rate BETWEEN 0 AND 1),
    maximum_subsidy_ratio numeric(9,6) NOT NULL CHECK (maximum_subsidy_ratio BETWEEN 0 AND 1),
    estimated_unit_cost numeric(19,2) NOT NULL CHECK (estimated_unit_cost >= 0),
    minimum_unit_margin numeric(19,2) NOT NULL CHECK (minimum_unit_margin >= 0),
    provenance varchar(200) NOT NULL CHECK (length(trim(provenance)) > 0),
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (vendor_id, version)
);

CREATE TABLE flash_sales (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES services(id) ON DELETE RESTRICT,
    option_id uuid NOT NULL REFERENCES service_options(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT','ENABLED','CANCELLED')),
    funding_source varchar(20) NOT NULL DEFAULT 'PLATFORM' CHECK (funding_source = 'PLATFORM'),
    unit_subsidy numeric(19,2) NOT NULL CHECK (unit_subsidy > 0),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL CHECK (starts_at < ends_at),
    quota integer NOT NULL CHECK (quota > 0),
    budget numeric(19,2) NOT NULL CHECK (budget > 0),
    original_unit_price numeric(19,2),
    policy_id uuid REFERENCES vendor_commission_policies(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status <> 'ENABLED' OR (original_unit_price IS NOT NULL AND original_unit_price > unit_subsidy AND policy_id IS NOT NULL)),
    EXCLUDE USING gist (option_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (status = 'ENABLED')
);
CREATE INDEX flash_sales_active_window ON flash_sales(starts_at,ends_at) WHERE status='ENABLED';

CREATE TABLE flash_sale_allocations (
    id uuid PRIMARY KEY,
    flash_sale_id uuid NOT NULL REFERENCES flash_sales(id) ON DELETE RESTRICT,
    booking_id uuid NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    booking_item_id uuid NOT NULL UNIQUE REFERENCES booking_items(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    state varchar(20) NOT NULL CHECK (state IN ('RESERVED','CONSUMED','RELEASED')),
    pricing_unit varchar(30) NOT NULL CHECK (pricing_unit IN ('PER_PERSON','PER_PACKAGE')),
    quantity integer NOT NULL CHECK (quantity > 0),
    original_unit_price numeric(19,2) NOT NULL CHECK (original_unit_price > 0),
    original_amount numeric(19,2) NOT NULL CHECK (original_amount = original_unit_price * quantity),
    policy_id uuid NOT NULL REFERENCES vendor_commission_policies(id) ON DELETE RESTRICT,
    policy_version integer NOT NULL,
    commission_rate numeric(9,6) NOT NULL CHECK (commission_rate BETWEEN 0 AND 1),
    commission_amount numeric(19,2) NOT NULL CHECK (commission_amount BETWEEN 0 AND original_amount AND commission_amount=round(original_amount*commission_rate,2)),
    unit_subsidy numeric(19,2) NOT NULL CHECK (unit_subsidy > 0),
    subsidy_amount numeric(19,2) NOT NULL CHECK (subsidy_amount = unit_subsidy * quantity),
    customer_payable numeric(19,2) NOT NULL CHECK (customer_payable = original_amount - subsidy_amount AND customer_payable > 0),
    vendor_receivable numeric(19,2) NOT NULL CHECK (vendor_receivable = original_amount - commission_amount),
    platform_contribution numeric(19,2) NOT NULL CHECK (platform_contribution = commission_amount - subsidy_amount AND platform_contribution >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX flash_sale_allocation_usage ON flash_sale_allocations(flash_sale_id,state);
CREATE INDEX flash_sale_allocation_booking ON flash_sale_allocations(booking_id);

CREATE FUNCTION guard_flash_sale_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE campaign flash_sales%ROWTYPE; configured_policy vendor_commission_policies%ROWTYPE; used_quantity bigint; used_budget numeric;
BEGIN
    IF TG_OP='UPDATE' THEN
        IF (to_jsonb(NEW)-'state'-'updated_at') IS DISTINCT FROM (to_jsonb(OLD)-'state'-'updated_at')
            OR OLD.state <> 'RESERVED' OR NEW.state NOT IN ('CONSUMED','RELEASED') THEN
            RAISE EXCEPTION 'Allocation snapshots are immutable; terminal transitions occur once';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO STRICT campaign FROM flash_sales WHERE id=NEW.flash_sale_id FOR UPDATE;
    SELECT * INTO STRICT configured_policy FROM vendor_commission_policies WHERE id=campaign.policy_id;
    IF NEW.policy_id IS DISTINCT FROM campaign.policy_id OR NEW.policy_version <> configured_policy.version
        OR NEW.commission_rate <> configured_policy.commission_rate
        OR NEW.original_unit_price IS DISTINCT FROM campaign.original_unit_price
        OR NEW.unit_subsidy <> campaign.unit_subsidy THEN
        RAISE EXCEPTION 'Allocation does not match the enabled financial policy snapshot';
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
CREATE TRIGGER flash_sale_allocation_guard BEFORE INSERT OR UPDATE ON flash_sale_allocations
    FOR EACH ROW EXECUTE FUNCTION guard_flash_sale_allocation();

CREATE TABLE flash_sale_hold_requests (
    customer_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_key varchar(120) NOT NULL,
    fingerprint varchar(64) NOT NULL,
    booking_id uuid NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (customer_id,request_key)
);

CREATE FUNCTION protect_commission_policy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Commission policies are append-only';
END $$;
CREATE TRIGGER commission_policy_immutable BEFORE UPDATE OR DELETE ON vendor_commission_policies
    FOR EACH ROW EXECUTE FUNCTION protect_commission_policy();

CREATE FUNCTION protect_enabled_flash_sale() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Only DRAFT flash sales can be deleted'; END IF;
        IF (to_jsonb(NEW) - 'status') IS DISTINCT FROM (to_jsonb(OLD) - 'status')
            OR NEW.status <> 'CANCELLED' THEN
            RAISE EXCEPTION 'Enabled/cancelled Flash Sale terms are immutable';
        END IF;
    END IF;
    RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER flash_sale_terms_immutable BEFORE UPDATE OR DELETE ON flash_sales
    FOR EACH ROW EXECUTE FUNCTION protect_enabled_flash_sale();

-- Both existing Booking/Order entry points update this aggregate. The transition and
-- reservation disposition commit together, including direct payment-webhook updates.
CREATE FUNCTION transition_flash_sale_allocations() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IN ('CANCELLED','REJECTED') THEN
        UPDATE flash_sale_allocations SET state='RELEASED',updated_at=CURRENT_TIMESTAMP
            WHERE booking_id=NEW.id AND state='RESERVED';
    ELSIF NEW.status='CONFIRMED' THEN
        UPDATE flash_sale_allocations SET state='CONSUMED',updated_at=CURRENT_TIMESTAMP
            WHERE booking_id=NEW.id AND state='RESERVED';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER flash_sale_booking_lifecycle AFTER UPDATE OF status ON bookings
    FOR EACH ROW WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION transition_flash_sale_allocations();
