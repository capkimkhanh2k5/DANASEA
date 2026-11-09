-- V55__ai_vendor_admin_copilot_and_insights.sql
-- Triển khai hệ thống Copilot đa phiên (Multi-Session Thread Control) cho Vendor và Admin,
-- lưu trữ bền vững phân tích Review Coaching, Smart Reply, Listing Optimizer, Slot Advisor,
-- Dispute Arbitration Summarizer và Cẩm nang biển 24h (Marine Briefing).

-- 1. Nâng cấp bảng ai_conversations của User để hỗ trợ Quản lý từng đoạn chat (Multi-Session Thread Control)
ALTER TABLE ai_conversations
    ADD COLUMN IF NOT EXISTS title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS context_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS context_id UUID,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_pinned 
ON ai_conversations(user_id, pinned, updated_at DESC);

-- 2. Bảng quản lý phiên hội thoại Copilot của Vendor & Admin (Multi-Session Thread Control)
CREATE TABLE IF NOT EXISTS ai_copilot_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID NOT NULL,
    actor_role VARCHAR(20) NOT NULL CHECK (actor_role IN ('VENDOR', 'ADMIN')),
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL DEFAULT 'vi',
    title VARCHAR(200),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    context_type VARCHAR(40),
    context_id UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_copilot_conv_actor 
ON ai_copilot_conversations(actor_user_id, actor_role, pinned, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_copilot_conv_vendor 
ON ai_copilot_conversations(vendor_id, updated_at DESC) 
WHERE vendor_id IS NOT NULL;

-- 3. Bảng tin nhắn và Artifact Cards của Copilot
CREATE TABLE IF NOT EXISTS ai_copilot_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_copilot_conversations(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    content TEXT,
    artifacts_json TEXT,
    tools_used VARCHAR(500),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_copilot_messages_conv 
ON ai_copilot_messages(conversation_id, created_at ASC);

-- 4. Bảng lưu trữ bền vững các bản phân tích Insights của Vendor
CREATE TABLE IF NOT EXISTS ai_vendor_insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    service_id UUID REFERENCES services(id) ON DELETE SET NULL,
    insight_type VARCHAR(50) NOT NULL,
    source_fingerprint VARCHAR(128),
    payload_json TEXT NOT NULL,
    analysis_method VARCHAR(50) NOT NULL DEFAULT 'HYBRID_DUAL_BRAIN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_vendor_insights_vendor 
ON ai_vendor_insights(vendor_id, insight_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_vendor_insights_service 
ON ai_vendor_insights(service_id, insight_type, created_at DESC) 
WHERE service_id IS NOT NULL;

-- 5. Bảng tóm tắt tranh chấp và gợi ý phán quyết trọng tài cho Admin
CREATE TABLE IF NOT EXISTS ai_dispute_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    sub_order_id UUID NOT NULL,
    requested_by_admin_id UUID NOT NULL,
    recommended_resolution VARCHAR(50),
    recommended_refund_percent INTEGER,
    summary_json TEXT NOT NULL,
    analysis_method VARCHAR(50) NOT NULL DEFAULT 'HYBRID_DUAL_BRAIN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_dispute_summaries_dispute UNIQUE (dispute_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_dispute_summaries_dispute 
ON ai_dispute_summaries(dispute_id);

-- 6. Bảng cẩm nang ra khơi & checklist 24h cho Khách hàng
CREATE TABLE IF NOT EXISTS ai_marine_briefings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    master_order_id UUID NOT NULL,
    sub_order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    slot_id UUID NOT NULL,
    weather_status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    briefing_json TEXT NOT NULL,
    notified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_marine_briefings_sub_order_customer UNIQUE (sub_order_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_marine_briefings_customer 
ON ai_marine_briefings(customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_marine_briefings_master_order 
ON ai_marine_briefings(master_order_id);
