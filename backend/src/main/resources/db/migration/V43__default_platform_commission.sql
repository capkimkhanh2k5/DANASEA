-- Owner-approved initial policy: 10 percent. Preserve any existing Admin policy.
LOCK TABLE system_configs IN SHARE ROW EXCLUSIVE MODE;
INSERT INTO system_configs (id, "key", "value", description, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM_COMMISSION_RATE', '10',
       'Default platform commission rate (percent)', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE "key" = 'PLATFORM_COMMISSION_RATE');
