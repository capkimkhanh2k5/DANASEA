-- ==============================================================================
-- V57__create_vendor_role_applications.sql
-- Mục đích: Quản lý Quy trình 1 (Đăng ký hợp tác và xét duyệt chuyển Role)
--          tách bạch hoàn toàn khỏi bảng vendors của Quy trình 2.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS vendor_role_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    business_name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    bank_account_number VARCHAR(255) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    bank_account_holder VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_partner_app_user UNIQUE (user_id),
    CONSTRAINT chk_partner_app_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_partner_app_status_created
    ON vendor_role_applications (status, created_at DESC);

COMMENT ON TABLE vendor_role_applications IS 'Lưu trữ đơn đăng ký hợp tác của CUSTOMER chờ Admin duyệt chuyển sang VENDOR (Quy trình 1)';
