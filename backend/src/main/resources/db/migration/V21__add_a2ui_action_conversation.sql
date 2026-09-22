ALTER TABLE a2ui_action ADD COLUMN conversation_id UUID;

CREATE INDEX idx_a2ui_action_pending_conversation
    ON a2ui_action (user_id, conversation_id, created_at)
    WHERE status = 'PENDING';
