-- Persist the attached entity (service, order, review...) per copilot message so the UI can rebuild context cards from history.
ALTER TABLE ai_copilot_messages ADD COLUMN IF NOT EXISTS context_type VARCHAR(50);
ALTER TABLE ai_copilot_messages ADD COLUMN IF NOT EXISTS context_id UUID;
ALTER TABLE ai_copilot_messages ADD COLUMN IF NOT EXISTS context_label VARCHAR(255);
