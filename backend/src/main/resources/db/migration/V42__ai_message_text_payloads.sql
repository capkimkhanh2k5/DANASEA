-- Chat replies, user context and tool arguments routinely exceed 255 characters.
ALTER TABLE ai_messages
    ALTER COLUMN content TYPE TEXT,
    ALTER COLUMN tool_calls TYPE TEXT;
