-- V54__dynamic_commission_rules.sql
-- Triển khai hệ thống quy tắc hoa hồng biến thiên (Onboarding 5%, Category 7%/10%/12%)
-- và Quỹ thưởng An toàn Biển 2% cộng trực tiếp tiền mặt vào bảng quyết toán (settlements).

-- 1. Bảng quản lý quy tắc hoa hồng động
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code VARCHAR(80) NOT NULL UNIQUE,
    rule_name VARCHAR(150) NOT NULL,
    target_type VARCHAR(50) NOT NULL CHECK (target_type IN ('GLOBAL', 'CATEGORY', 'VENDOR', 'ONBOARDING')),
    category_id UUID REFERENCES categories(id) ON DELETE CASCADE,
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    commission_rate NUMERIC(5, 4) NOT NULL CHECK (commission_rate >= 0.0000 AND commission_rate <= 1.0000),
    duration_days INTEGER CHECK (duration_days IS NULL OR duration_days > 0),
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMP WITH TIME ZONE,
    effective_to TIMESTAMP WITH TIME ZONE,
    description TEXT,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup 
ON commission_rules (target_type, is_active, priority DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_rules_category 
ON commission_rules (category_id) WHERE target_type = 'CATEGORY' AND category_id IS NOT NULL;

-- 2. Thêm các cột lưu thưởng An toàn Biển (Safety Rebate 2%) trực tiếp vào bảng settlements
ALTER TABLE settlements
    ADD COLUMN IF NOT EXISTS safety_rebate_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.0000 CHECK (safety_rebate_rate >= 0.0000 AND safety_rebate_rate <= 1.0000),
    ADD COLUMN IF NOT EXISTS safety_rebate_amount NUMERIC(38, 2) NOT NULL DEFAULT 0.00 CHECK (safety_rebate_amount >= 0.00),
    ADD COLUMN IF NOT EXISTS safety_rebate_qualified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS safety_rebate_note VARCHAR(255);

-- 3. Cấu hình tham số hệ thống cho Quỹ thưởng An toàn Biển trong system_configs
INSERT INTO system_configs (id, "key", "value", description, created_at, updated_at)
SELECT gen_random_uuid(), 'SAFETY_REBATE_RATE', '2.0',
       'Tỷ lệ thưởng an toàn biển và chất lượng dịch vụ cộng tiền mặt khi quyết toán (%)', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE "key" = 'SAFETY_REBATE_RATE');

INSERT INTO system_configs (id, "key", "value", description, created_at, updated_at)
SELECT gen_random_uuid(), 'SAFETY_REBATE_ENABLED', 'true',
       'Bật/tắt chế độ cộng thưởng tiền mặt an toàn biển khi quyết toán', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE "key" = 'SAFETY_REBATE_ENABLED');

-- 4. Seed dữ liệu quy tắc hoa hồng ban đầu
-- 4.1. Rule Onboarding: 5% cho vendor mới trong 90 ngày (3 tháng)
INSERT INTO commission_rules (rule_code, rule_name, target_type, commission_rate, duration_days, priority, is_active, description)
VALUES (
    'ONBOARDING_NEW_VENDOR_90D',
    'Ưu đãi Đối tác mới (3 tháng đầu)',
    'ONBOARDING',
    0.0500,
    90,
    100,
    true,
    'Áp dụng mức hoa hồng ưu đãi 5.0% cho đối tác mới trong 90 ngày đầu kể từ ngày duyệt hồ sơ'
)
ON CONFLICT (rule_code) DO NOTHING;

-- 4.2. Nhóm 1: 7.0% (Tiện ích & Cho thuê dụng cụ biển)
INSERT INTO commission_rules (rule_code, rule_name, target_type, category_id, commission_rate, priority, is_active, description)
SELECT 
    'CATEGORY_' || UPPER(REPLACE(slug, '-', '_')),
    'Biểu phí thuê dụng cụ: ' || name,
    'CATEGORY',
    id,
    0.0700,
    50,
    true,
    'Nhóm tiện ích và cho thuê dụng cụ biển (7.0%)'
FROM categories 
WHERE slug IN ('thue-sup', 'thue-kayak', 'thue-van-luot-song')
ON CONFLICT (rule_code) DO NOTHING;

-- 4.3. Nhóm 2: 10.0% (Thể thao biển phổ thông)
INSERT INTO commission_rules (rule_code, rule_name, target_type, category_id, commission_rate, priority, is_active, description)
SELECT 
    'CATEGORY_' || UPPER(REPLACE(slug, '-', '_')),
    'Biểu phí thể thao biển: ' || name,
    'CATEGORY',
    id,
    0.1000,
    50,
    true,
    'Nhóm hoạt động thể thao biển phổ thông (10.0%)'
FROM categories 
WHERE slug IN ('cano-du-bay', 'mo-to-nuoc-jetski', 'truot-phao-chuoi', 'cheo-sup-kayak')
ON CONFLICT (rule_code) DO NOTHING;

-- 4.4. Nhóm 3: 12.0% (Tour trải nghiệm chuyên sâu & Lặn biển)
INSERT INTO commission_rules (rule_code, rule_name, target_type, category_id, commission_rate, priority, is_active, description)
SELECT 
    'CATEGORY_' || UPPER(REPLACE(slug, '-', '_')),
    'Biểu phí tour chuyên sâu: ' || name,
    'CATEGORY',
    id,
    0.1200,
    50,
    true,
    'Nhóm tour trải nghiệm chuyên sâu và lặn biển (12.0%)'
FROM categories 
WHERE slug IN ('lan-ngam-san-ho', 'cau-ca-bien', 'cau-muc-dem', 'hoc-luot-song', 'surf-tour-co-huong-dan', 'du-thuyen-ngam-hoang-hon')
ON CONFLICT (rule_code) DO NOTHING;
